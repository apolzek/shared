import csv
import re
from datetime import datetime
import glob
import os
from pathlib import Path


def to_datetime(date_str, time_str):
    return datetime.strptime(date_str + time_str, "%y%m%d%H%M%S")


def parse_log_with_datetime(log_lines):
    data = []
    for line in log_lines:
        parts = line.strip().split()
        if len(parts) < 2:
            continue
        dt = to_datetime(parts[0], parts[1])
        content = " ".join(parts[2:])
        data.append((dt, content))
    return data


# Load all templates từ CSV (chỉ cần chạy một lần khi import)
def load_templates(csv_path="/opt/flink/jobs/event_template.csv"):
    templates = []
    with open(csv_path, "r") as f:
        reader = csv.DictReader(f)
        for row in reader:
            raw_template = row["EventTemplate"]
            pattern = re.escape(raw_template).replace(r"\[\*\]", r"(.*?)")
            templates.append((
                row["EventId"],  # E5
                raw_template,  # [*]Receiving block[*]src:[*]dest:[*]
                re.compile(pattern)  # regex compiled
            ))
    return templates


def delete_log():
    LOG_DIR = str(Path(__file__).resolve().parent / 'logs')
    for f in glob.glob(f"{LOG_DIR}/*.log"):
        os.remove(f)
