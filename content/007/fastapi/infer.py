# cardinality_detector.py
"""
API para Detecção de Alta Cardinalidade em Labels de TSDB
Usando Machine Learning para identificar labels que podem explodir métricas
"""

import uvicorn
import asyncio
import logging
import hashlib
import time
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Set, Tuple, Any
from collections import defaultdict, Counter
from dataclasses import dataclass, asdict
from enum import Enum

import numpy as np
import pandas as pd
from fastapi import FastAPI, HTTPException, BackgroundTasks, Depends
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field, validator
from sklearn.ensemble import IsolationForest
from sklearn.preprocessing import StandardScaler
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.cluster import DBSCAN
from sklearn.metrics.pairwise import cosine_similarity

# Configuração de logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Enums e constantes
class RiskLevel(str, Enum):
    LOW = "low"
    MEDIUM = "medium"
    HIGH = "high"
    CRITICAL = "critical"

class CardinalityPattern(str, Enum):
    UUID = "uuid"
    TIMESTAMP = "timestamp"
    IP_ADDRESS = "ip_address"
    USER_ID = "user_id"
    SESSION_ID = "session_id"
    RANDOM_STRING = "random_string"
    INCREMENTAL = "incremental"
    UNKNOWN = "unknown"

# Configurações
CARDINALITY_THRESHOLDS = {
    RiskLevel.LOW: 100,
    RiskLevel.MEDIUM: 1000,
    RiskLevel.HIGH: 10000,
    RiskLevel.CRITICAL: 100000
}

# Modelos Pydantic
class MetricLabel(BaseModel):
    name: str = Field(..., description="Nome do label")
    values: List[str] = Field(..., description="Lista de valores únicos do label")
    metric_name: str = Field(..., description="Nome da métrica associada")
    timestamp: Optional[datetime] = Field(default_factory=datetime.now)

class CardinalityAnalysisRequest(BaseModel):
    labels: List[MetricLabel] = Field(..., description="Lista de labels para análise")
    time_window_hours: int = Field(default=24, ge=1, le=168, description="Janela de tempo em horas")
    threshold_override: Optional[Dict[str, int]] = Field(None, description="Override dos thresholds padrão")

class CardinalityInsight(BaseModel):
    label_name: str
    metric_name: str
    current_cardinality: int
    estimated_growth_rate: float
    pattern_detected: CardinalityPattern
    risk_level: RiskLevel
    predicted_cardinality_24h: int
    recommendations: List[str]
    confidence_score: float

class CardinalityAnalysisResponse(BaseModel):
    analysis_id: str
    timestamp: datetime
    total_labels_analyzed: int
    high_risk_labels: List[CardinalityInsight]
    summary: Dict[str, Any]
    execution_time_ms: float

# Classes de análise
@dataclass
class CardinalityMetrics:
    current_count: int
    growth_rate: float
    pattern: CardinalityPattern
    entropy: float
    uniqueness_ratio: float
    temporal_distribution: Dict[str, int]

class PatternDetector:
    """Detecta padrões em valores de labels que indicam alta cardinalidade"""
    
    def __init__(self):
        self.uuid_patterns = [
            r'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$',
            r'^[0-9a-f]{32}$',
            r'^[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}$'
        ]
        self.timestamp_patterns = [
            r'^\d{10}$',  # Unix timestamp
            r'^\d{13}$',  # Unix timestamp em ms
            r'^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}',  # ISO format
        ]
        self.ip_patterns = [
            r'^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$',
            r'^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$'
        ]
    
    def detect_pattern(self, values: List[str]) -> CardinalityPattern:
        """Detecta o padrão predominante nos valores"""
        import re
        
        if not values:
            return CardinalityPattern.UNKNOWN
        
        sample_size = min(100, len(values))
        sample_values = values[:sample_size]
        
        # Testa padrões UUID
        uuid_matches = sum(1 for v in sample_values 
                          for pattern in self.uuid_patterns 
                          if re.match(pattern, v))
        if uuid_matches / len(sample_values) > 0.8:
            return CardinalityPattern.UUID
        
        # Testa padrões de timestamp
        timestamp_matches = sum(1 for v in sample_values 
                               for pattern in self.timestamp_patterns 
                               if re.match(pattern, v))
        if timestamp_matches / len(sample_values) > 0.8:
            return CardinalityPattern.TIMESTAMP
        
        # Testa padrões de IP
        ip_matches = sum(1 for v in sample_values 
                        for pattern in self.ip_patterns 
                        if re.match(pattern, v))
        if ip_matches / len(sample_values) > 0.8:
            return CardinalityPattern.IP_ADDRESS
        
        # Testa se são incrementais
        try:
            numeric_values = [int(v) for v in sample_values if v.isdigit()]
            if len(numeric_values) > len(sample_values) * 0.8:
                if self._is_incremental(numeric_values):
                    return CardinalityPattern.INCREMENTAL
        except:
            pass
        
        # Testa se são user IDs (padrão comum)
        if any(keyword in sample_values[0].lower() for keyword in ['user', 'usr', 'uid'] if sample_values):
            return CardinalityPattern.USER_ID
        
        # Testa se são session IDs
        if any(keyword in sample_values[0].lower() for keyword in ['session', 'sess', 'sid'] if sample_values):
            return CardinalityPattern.SESSION_ID
        
        # Analisa entropia para detectar strings aleatórias
        if self._has_high_entropy(sample_values):
            return CardinalityPattern.RANDOM_STRING
        
        return CardinalityPattern.UNKNOWN
    
    def _is_incremental(self, values: List[int]) -> bool:
        """Verifica se os valores são incrementais"""
        if len(values) < 3:
            return False
        
        sorted_values = sorted(values)
        differences = [sorted_values[i+1] - sorted_values[i] for i in range(len(sorted_values)-1)]
        avg_diff = np.mean(differences)
        return np.std(differences) / avg_diff < 0.1 if avg_diff > 0 else False
    
    def _has_high_entropy(self, values: List[str]) -> bool:
        """Calcula entropia dos valores para detectar aleatoriedade"""
        if not values:
            return False
        
        # Concatena todos os valores e calcula entropia de caracteres
        all_chars = ''.join(values)
        char_counts = Counter(all_chars)
        total_chars = len(all_chars)
        
        entropy = -sum((count/total_chars) * np.log2(count/total_chars) 
                      for count in char_counts.values())
        
        # Entropia alta indica aleatoriedade
        return entropy > 4.0

class CardinalityAnalyzer:
    """Analisador principal de cardinalidade usando ML"""
    
    def __init__(self):
        self.pattern_detector = PatternDetector()
        self.isolation_forest = IsolationForest(contamination=0.1, random_state=42)
        self.scaler = StandardScaler()
        self.historical_data = defaultdict(list)
        
    def analyze_label(self, label: MetricLabel) -> CardinalityMetrics:
        """Analisa um label específico"""
        values = label.values
        current_count = len(set(values))  # Cardinalidade atual
        
        # Detecta padrão
        pattern = self.pattern_detector.detect_pattern(values)
        
        # Calcula entropia
        entropy = self._calculate_entropy(values)
        
        # Taxa de crescimento (simulada baseada no padrão)
        growth_rate = self._estimate_growth_rate(pattern, current_count)
        
        # Ratio de uniqueness
        uniqueness_ratio = len(set(values)) / len(values) if values else 0
        
        # Distribuição temporal (simulada)
        temporal_dist = self._simulate_temporal_distribution(values)
        
        return CardinalityMetrics(
            current_count=current_count,
            growth_rate=growth_rate,
            pattern=pattern,
            entropy=entropy,
            uniqueness_ratio=uniqueness_ratio,
            temporal_distribution=temporal_dist
        )
    
    def _calculate_entropy(self, values: List[str]) -> float:
        """Calcula entropia de Shannon dos valores"""
        if not values:
            return 0.0
        
        value_counts = Counter(values)
        total = len(values)
        entropy = -sum((count/total) * np.log2(count/total) 
                      for count in value_counts.values())
        return entropy
    
    def _estimate_growth_rate(self, pattern: CardinalityPattern, current_count: int) -> float:
        """Estima taxa de crescimento baseada no padrão"""
        growth_rates = {
            CardinalityPattern.UUID: 0.95,          # Muito alto
            CardinalityPattern.TIMESTAMP: 0.90,     # Muito alto
            CardinalityPattern.RANDOM_STRING: 0.85, # Alto
            CardinalityPattern.SESSION_ID: 0.80,    # Alto
            CardinalityPattern.USER_ID: 0.60,       # Médio-alto
            CardinalityPattern.IP_ADDRESS: 0.40,    # Médio
            CardinalityPattern.INCREMENTAL: 0.70,   # Médio-alto
            CardinalityPattern.UNKNOWN: 0.30        # Baixo
        }
        
        base_rate = growth_rates.get(pattern, 0.30)
        
        # Ajusta baseado na cardinalidade atual
        if current_count > 10000:
            base_rate *= 1.2
        elif current_count > 1000:
            base_rate *= 1.1
        
        return min(base_rate, 0.99)
    
    def _simulate_temporal_distribution(self, values: List[str]) -> Dict[str, int]:
        """Simula distribuição temporal dos valores"""
        # Em um cenário real, isso analisaria timestamps reais
        total = len(values)
        return {
            "last_hour": int(total * 0.4),
            "last_6h": int(total * 0.7),
            "last_24h": total
        }
    
    def predict_cardinality_24h(self, metrics: CardinalityMetrics) -> int:
        """Prediz cardinalidade em 24h"""
        current = metrics.current_count
        growth_rate = metrics.growth_rate
        
        # Modelo exponencial simples
        predicted = current * (1 + growth_rate)
        
        # Ajustes baseados no padrão
        pattern_multipliers = {
            CardinalityPattern.UUID: 2.0,
            CardinalityPattern.TIMESTAMP: 1.8,
            CardinalityPattern.RANDOM_STRING: 1.6,
            CardinalityPattern.SESSION_ID: 1.4,
            CardinalityPattern.USER_ID: 1.2,
            CardinalityPattern.IP_ADDRESS: 1.1,
            CardinalityPattern.INCREMENTAL: 1.3,
            CardinalityPattern.UNKNOWN: 1.0
        }
        
        multiplier = pattern_multipliers.get(metrics.pattern, 1.0)
        return int(predicted * multiplier)
    
    def assess_risk_level(self, current_cardinality: int, predicted_cardinality: int) -> RiskLevel:
        """Avalia nível de risco baseado na cardinalidade"""
        max_cardinality = max(current_cardinality, predicted_cardinality)
        
        if max_cardinality >= CARDINALITY_THRESHOLDS[RiskLevel.CRITICAL]:
            return RiskLevel.CRITICAL
        elif max_cardinality >= CARDINALITY_THRESHOLDS[RiskLevel.HIGH]:
            return RiskLevel.HIGH
        elif max_cardinality >= CARDINALITY_THRESHOLDS[RiskLevel.MEDIUM]:
            return RiskLevel.MEDIUM
        else:
            return RiskLevel.LOW
    
    def generate_recommendations(self, metrics: CardinalityMetrics, risk_level: RiskLevel) -> List[str]:
        """Gera recomendações baseadas na análise"""
        recommendations = []
        
        if risk_level in [RiskLevel.HIGH, RiskLevel.CRITICAL]:
            recommendations.append("⚠️ AÇÃO IMEDIATA: Considere aplicar sampling ou aggregation")
            recommendations.append("🔧 Implemente rate limiting para esta métrica")
            
        if metrics.pattern == CardinalityPattern.UUID:
            recommendations.append("🆔 UUIDs detectados - considere hash ou prefix truncation")
            
        if metrics.pattern == CardinalityPattern.TIMESTAMP:
            recommendations.append("⏰ Timestamps detectados - agrupe por intervalos (5min, 1h)")
            
        if metrics.pattern == CardinalityPattern.IP_ADDRESS:
            recommendations.append("🌐 IPs detectados - considere subnets (/24, /16)")
            
        if metrics.uniqueness_ratio > 0.9:
            recommendations.append("📊 Alta uniqueness - implemente drop rules para valores raros")
            
        if metrics.growth_rate > 0.8:
            recommendations.append("📈 Alta taxa de crescimento - monitore closely")
            
        recommendations.append("📋 Configure alertas para cardinalidade > {}".format(
            CARDINALITY_THRESHOLDS[risk_level]))
        
        return recommendations

# FastAPI App
app = FastAPI(
    title="High Cardinality Detection API",
    description="API para detectar métricas de alta cardinalidade em TSDBs usando Machine Learning",
    version="2.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Instância global do analisador
analyzer = CardinalityAnalyzer()

@app.post("/analyze/cardinality", response_model=CardinalityAnalysisResponse)
async def analyze_cardinality(request: CardinalityAnalysisRequest):
    """Endpoint principal para análise de cardinalidade"""
    start_time = time.time()
    
    try:
        # Gera ID único para esta análise
        analysis_id = hashlib.md5(
            f"{datetime.now().isoformat()}{len(request.labels)}".encode()
        ).hexdigest()[:12]
        
        high_risk_insights = []
        total_analyzed = len(request.labels)
        
        # Analisa cada label
        for label in request.labels:
            logger.info(f"Analisando label: {label.name} da métrica: {label.metric_name}")
            
            # Análise de cardinalidade
            metrics = analyzer.analyze_label(label)
            
            # Predição 24h
            predicted_24h = analyzer.predict_cardinality_24h(metrics)
            
            # Avaliação de risco
            risk_level = analyzer.assess_risk_level(metrics.current_count, predicted_24h)
            
            # Gera recomendações
            recommendations = analyzer.generate_recommendations(metrics, risk_level)
            
            # Score de confiança baseado na qualidade dos dados
            confidence_score = min(0.95, 0.5 + (len(label.values) / 1000) * 0.45)
            
            # Cria insight
            insight = CardinalityInsight(
                label_name=label.name,
                metric_name=label.metric_name,
                current_cardinality=metrics.current_count,
                estimated_growth_rate=metrics.growth_rate,
                pattern_detected=metrics.pattern,
                risk_level=risk_level,
                predicted_cardinality_24h=predicted_24h,
                recommendations=recommendations,
                confidence_score=confidence_score
            )
            
            # Adiciona apenas os de risco médio ou alto
            if risk_level in [RiskLevel.MEDIUM, RiskLevel.HIGH, RiskLevel.CRITICAL]:
                high_risk_insights.append(insight)
        
        # Calcula tempo de execução
        execution_time = (time.time() - start_time) * 1000
        
        # Gera sumário
        summary = {
            "critical_labels": len([i for i in high_risk_insights if i.risk_level == RiskLevel.CRITICAL]),
            "high_risk_labels": len([i for i in high_risk_insights if i.risk_level == RiskLevel.HIGH]),
            "medium_risk_labels": len([i for i in high_risk_insights if i.risk_level == RiskLevel.MEDIUM]),
            "total_current_cardinality": sum(i.current_cardinality for i in high_risk_insights),
            "total_predicted_cardinality": sum(i.predicted_cardinality_24h for i in high_risk_insights),
            "most_common_pattern": max([i.pattern_detected for i in high_risk_insights], 
                                     key=[i.pattern_detected for i in high_risk_insights].count) 
                                     if high_risk_insights else None,
            "avg_confidence_score": np.mean([i.confidence_score for i in high_risk_insights]) 
                                   if high_risk_insights else 0.0
        }
        
        logger.info(f"Análise concluída: {len(high_risk_insights)} labels de risco encontrados")
        
        return CardinalityAnalysisResponse(
            analysis_id=analysis_id,
            timestamp=datetime.now(),
            total_labels_analyzed=total_analyzed,
            high_risk_labels=high_risk_insights,
            summary=summary,
            execution_time_ms=execution_time
        )
        
    except Exception as e:
        logger.error(f"Erro na análise: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Erro interno: {str(e)}")

@app.get("/health")
async def health_check():
    """Health check endpoint"""
    return {
        "status": "healthy",
        "service": "high-cardinality-detector",
        "version": "2.0.0",
        "timestamp": datetime.now().isoformat()
    }

@app.get("/patterns")
async def get_supported_patterns():
    """Retorna padrões suportados pela detecção"""
    return {
        "supported_patterns": [pattern.value for pattern in CardinalityPattern],
        "risk_levels": [level.value for level in RiskLevel],
        "cardinality_thresholds": CARDINALITY_THRESHOLDS
    }

@app.post("/simulate/data")
async def simulate_high_cardinality_data():
    """Gera dados de exemplo para teste"""
    import uuid
    import random
    
    # Dados simulados de alta cardinalidade
    test_labels = [
        MetricLabel(
            name="request_id",
            values=[str(uuid.uuid4()) for _ in range(5000)],  # UUIDs
            metric_name="http_requests_total"
        ),
        MetricLabel(
            name="user_session",
            values=[f"sess_{random.randint(100000, 999999)}" for _ in range(3000)],
            metric_name="user_activity"
        ),
        MetricLabel(
            name="client_ip", 
            values=[f"192.168.{random.randint(1,255)}.{random.randint(1,255)}" for _ in range(1000)],
            metric_name="network_connections"
        ),
        MetricLabel(
            name="error_code",
            values=["404", "500", "403", "401"] * 100,  # Baixa cardinalidade
            metric_name="http_errors"
        )
    ]
    
    return {"test_labels": len(test_labels), "message": "Dados simulados gerados"}

if __name__ == "__main__":
    print("🚀 Iniciando High Cardinality Detection API...")
    print("📍 URL: http://localhost:8000")
    print("🔍 Docs: http://localhost:8000/docs")
    print("❤️  Health: http://localhost:8000/health")
    print("🧪 Simulate Data: http://localhost:8000/simulate/data")
    
    uvicorn.run(
        app, 
        host="0.0.0.0", 
        port=8000, 
        log_level="info",
        reload=False
    )