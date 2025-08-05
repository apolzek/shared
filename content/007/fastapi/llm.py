from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import Dict, List, Any, Optional
import httpx
import json
import logging
from datetime import datetime

# Configuração de logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="Prometheus Metrics Cardinality Analyzer",
    description="Analisa métricas do Prometheus para identificar alta cardinalidade usando Ollama Llama3.1 8B",
    version="1.0.0"
)

# Modelos Pydantic
class PrometheusMetric(BaseModel):
    metric: Dict[str, Any]
    value: List[Any]
    values: Optional[List[List[Any]]] = None

class MetricAnalysisRequest(BaseModel):
    metrics: List[PrometheusMetric]
    metric_name: Optional[str] = None
    threshold_high_cardinality: Optional[int] = 1000

class CardinalityAnalysis(BaseModel):
    is_high_cardinality: bool
    total_series: int
    high_cardinality_labels: List[str]
    cardinality_score: float
    recommendations: List[str]
    analysis_timestamp: str

class MetricAnalysisResponse(BaseModel):
    metric_name: str
    analysis: CardinalityAnalysis
    llm_reasoning: str

# Configuração do Ollama
OLLAMA_BASE_URL = "http://localhost:11434"
MODEL_NAME = "llama3.1:8b"  # Modelo disponível no Ollama

class OllamaClient:
    def __init__(self, base_url: str = OLLAMA_BASE_URL):
        self.base_url = base_url
        
    async def generate_response(self, prompt: str) -> str:
        """Chama o modelo Ollama Llama3.1 8B para análise"""
        try:
            async with httpx.AsyncClient(timeout=60.0) as client:
                payload = {
                    "model": MODEL_NAME,
                    "prompt": prompt,
                    "stream": False,
                    "options": {
                        "temperature": 0.1,
                        "top_p": 0.9,
                        "num_predict": 2000
                    }
                }
                
                response = await client.post(
                    f"{self.base_url}/api/generate",
                    json=payload
                )
                
                if response.status_code == 200:
                    result = response.json()
                    return result.get("response", "")
                else:
                    raise HTTPException(
                        status_code=500, 
                        detail=f"Erro ao conectar com Ollama: {response.status_code}"
                    )
                    
        except httpx.TimeoutException:
            raise HTTPException(
                status_code=504, 
                detail="Timeout ao conectar com Ollama"
            )
        except Exception as e:
            logger.error(f"Erro na chamada do Ollama: {str(e)}")
            raise HTTPException(
                status_code=500, 
                detail=f"Erro interno: {str(e)}"
            )

class CardinalityAnalyzer:
    def __init__(self):
        self.ollama_client = OllamaClient()
        
    def analyze_metric_structure(self, metrics: List[PrometheusMetric]) -> Dict[str, Any]:
        """Analisa a estrutura das métricas para extrair informações de cardinalidade"""
        if not metrics:
            return {"total_series": 0, "unique_labels": {}, "label_combinations": 0}
            
        unique_labels = {}
        total_series = len(metrics)
        
        for metric in metrics:
            metric_labels = metric.metric
            for label_name, label_value in metric_labels.items():
                if label_name == "__name__":
                    continue
                    
                if label_name not in unique_labels:
                    unique_labels[label_name] = set()
                unique_labels[label_name].add(str(label_value))
        
        # Calcula cardinalidade por label
        label_cardinalities = {
            label: len(values) for label, values in unique_labels.items()
        }
        
        return {
            "total_series": total_series,
            "unique_labels": unique_labels,
            "label_cardinalities": label_cardinalities,
            "total_unique_labels": len(unique_labels)
        }
    
    def create_analysis_prompt(self, metric_analysis: Dict[str, Any], metric_name: str, threshold: int) -> str:
        """Cria o prompt para o LLM analisar a métrica"""
        
        # Identifica labels de alta cardinalidade por padrão
        high_cardinality_patterns = [
            'id', 'user_id', 'session_id', 'request_id', 'trace_id', 'span_id',
            'transaction_id', 'correlation_id', 'uuid', 'guid', 'hash',
            'instance', 'container_id', 'pod_name', 'node_name'
        ]
        
        detected_problematic_labels = []
        all_labels = list(metric_analysis.get('label_cardinalities', {}).keys())
        
        for label in all_labels:
            label_lower = label.lower()
            for pattern in high_cardinality_patterns:
                if pattern in label_lower:
                    detected_problematic_labels.append(label)
                    break
        
        prompt = f"""
Você é um especialista em Time Series Databases (TSDB) e métricas do Prometheus. 
Analise a seguinte métrica e determine se ela possui alta cardinalidade.

MÉTRICA: {metric_name}
THRESHOLD DE ALTA CARDINALIDADE: {threshold} séries

DADOS DA MÉTRICA:
- Total de séries temporais observadas: {metric_analysis['total_series']}
- Número de labels únicos: {metric_analysis['total_unique_labels']}
- Labels presentes: {all_labels}
- Cardinalidade por label: {json.dumps(metric_analysis['label_cardinalities'], indent=2)}

⚠️  LABELS PROBLEMÁTICOS DETECTADOS: {detected_problematic_labels}

CRITÉRIOS CRÍTICOS DE ANÁLISE:
1. ATENÇÃO: Mesmo com poucas séries observadas, labels como 'id', 'user_id', 'session_id', 'instance' indicam ALTA CARDINALIDADE POTENCIAL
2. Labels com IDs únicos/dinâmicos podem gerar milhares de séries temporais em produção
3. Uma única métrica com label 'id' pode facilmente ultrapassar {threshold} séries
4. Considere o POTENCIAL de crescimento, não apenas o número atual de séries
5. Labels como 'id', 'user_id', 'request_id', 'session_id', 'instance', 'container_id' são BANDEIRAS VERMELHAS

REGRA IMPORTANTE: 
- Se existir qualquer label com padrão de ID único (id, user_id, session_id, etc.), considere ALTA CARDINALIDADE mesmo com poucas amostras
- Analise o potencial de crescimento, não apenas o estado atual

RESPONDA EM FORMATO JSON com a seguinte estrutura:
{{
    "is_high_cardinality": boolean,
    "cardinality_score": float (0-10, onde 10 é extremamente alta),
    "high_cardinality_labels": [lista de labels que contribuem para alta cardinalidade],
    "recommendations": [lista de recomendações para reduzir cardinalidade],
    "reasoning": "explicação detalhada considerando o potencial de crescimento dos labels"
}}

Seja preciso e considere o POTENCIAL de cardinalidade, não apenas o estado atual.
"""
        return prompt
    
    async def analyze_cardinality(self, metrics: List[PrometheusMetric], metric_name: str, threshold: int) -> MetricAnalysisResponse:
        """Analisa a cardinalidade das métricas usando o LLM"""
        
        # Análise estrutural da métrica
        metric_analysis = self.analyze_metric_structure(metrics)
        
        # Cria prompt para o LLM
        prompt = self.create_analysis_prompt(metric_analysis, metric_name, threshold)
        
        # Chama o LLM
        llm_response = await self.ollama_client.generate_response(prompt)
        
        try:
            # Tenta extrair JSON da resposta do LLM
            llm_json = self.extract_json_from_response(llm_response)
            
            # Cria análise estruturada
            analysis = CardinalityAnalysis(
                is_high_cardinality=llm_json.get("is_high_cardinality", metric_analysis["total_series"] > threshold),
                total_series=metric_analysis["total_series"],
                high_cardinality_labels=llm_json.get("high_cardinality_labels", []),
                cardinality_score=llm_json.get("cardinality_score", 0.0),
                recommendations=llm_json.get("recommendations", []),
                analysis_timestamp=datetime.now().isoformat()
            )
            
            return MetricAnalysisResponse(
                metric_name=metric_name,
                analysis=analysis,
                llm_reasoning=llm_json.get("reasoning", llm_response)
            )
            
        except Exception as e:
            logger.error(f"Erro ao processar resposta do LLM: {str(e)}")
            
            # Fallback para análise básica com detecção de padrões problemáticos
            high_cardinality_patterns = [
                'id', 'user_id', 'session_id', 'request_id', 'trace_id', 'span_id',
                'transaction_id', 'correlation_id', 'uuid', 'guid', 'hash',
                'instance', 'container_id', 'pod_name', 'node_name'
            ]
            
            problematic_labels = []
            all_labels = list(metric_analysis.get("label_cardinalities", {}).keys())
            
            for label in all_labels:
                label_lower = label.lower()
                for pattern in high_cardinality_patterns:
                    if pattern in label_lower:
                        problematic_labels.append(label)
                        break
            
            # Se há labels problemáticos, considera alta cardinalidade
            is_high = (metric_analysis["total_series"] > threshold) or (len(problematic_labels) > 0)
            
            high_cardinality_labels = problematic_labels + [
                label for label, count in metric_analysis["label_cardinalities"].items() 
                if count > 100 and label not in problematic_labels
            ]
            
            # Score baseado em labels problemáticos
            base_score = min(metric_analysis["total_series"] / threshold * 5, 10.0)
            pattern_penalty = len(problematic_labels) * 3  # 3 pontos por label problemático
            final_score = min(base_score + pattern_penalty, 10.0)
            
            recommendations = []
            if problematic_labels:
                recommendations.extend([
                    f"CRÍTICO: Remover ou agregar labels de ID único: {', '.join(problematic_labels)}",
                    "Considerar usar recording rules para agregar métricas",
                    "Implementar sampling ou limitação de cardinalidade"
                ])
            else:
                recommendations.extend([
                    "Revisar labels com alta variabilidade", 
                    "Considerar agregação de métricas"
                ])
            
            analysis = CardinalityAnalysis(
                is_high_cardinality=is_high,
                total_series=metric_analysis["total_series"],
                high_cardinality_labels=high_cardinality_labels,
                cardinality_score=final_score,
                recommendations=recommendations,
                analysis_timestamp=datetime.now().isoformat()
            )
            
            return MetricAnalysisResponse(
                metric_name=metric_name,
                analysis=analysis,
                llm_reasoning=llm_response
            )
    
    def extract_json_from_response(self, response: str) -> Dict[str, Any]:
        """Extrai JSON da resposta do LLM"""
        try:
            # Procura por JSON na resposta
            start_idx = response.find('{')
            end_idx = response.rfind('}') + 1
            
            if start_idx != -1 and end_idx != -1:
                json_str = response[start_idx:end_idx]
                return json.loads(json_str)
            else:
                raise ValueError("JSON não encontrado na resposta")
                
        except Exception as e:
            logger.error(f"Erro ao extrair JSON: {str(e)}")
            raise

# Instância do analisador
analyzer = CardinalityAnalyzer()

@app.post("/analyze-cardinality", response_model=MetricAnalysisResponse)
async def analyze_metric_cardinality(request: MetricAnalysisRequest):
    """
    Analisa métricas do Prometheus para identificar alta cardinalidade
    """
    try:
        if not request.metrics:
            raise HTTPException(status_code=400, detail="Nenhuma métrica fornecida")
        
        # Determina o nome da métrica
        metric_name = request.metric_name
        if not metric_name and request.metrics:
            # Tenta extrair o nome da primeira métrica
            first_metric = request.metrics[0].metric
            metric_name = first_metric.get("__name__", "unknown_metric")
        
        # Analisa a cardinalidade
        result = await analyzer.analyze_cardinality(
            metrics=request.metrics,
            metric_name=metric_name,
            threshold=request.threshold_high_cardinality
        )
        
        return result
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Erro na análise: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Erro interno: {str(e)}")

@app.get("/health")
async def health_check():
    """Verifica a saúde do serviço"""
    try:
        # Testa conectividade com Ollama
        async with httpx.AsyncClient(timeout=5.0) as client:
            response = await client.get(f"{OLLAMA_BASE_URL}/api/tags")
            ollama_status = "connected" if response.status_code == 200 else "disconnected"
    except:
        ollama_status = "disconnected"
    
    return {
        "status": "healthy",
        "timestamp": datetime.now().isoformat(),
        "ollama_status": ollama_status,
        "model": MODEL_NAME
    }

@app.get("/")
async def root():
    """Endpoint raiz com informações da API"""
    return {
        "message": "Prometheus Metrics Cardinality Analyzer",
        "version": "1.0.0",
        "endpoints": {
            "analyze": "/analyze-cardinality",
            "health": "/health",
            "docs": "/docs"
        }
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)