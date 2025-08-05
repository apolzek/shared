import logging
from pathlib import Path

LOG_DIR = Path(__file__).resolve().parent / 'logs'
LOG_DIR.mkdir(parents=True, exist_ok=True)

logger = logging.getLogger("sim")
logger.setLevel(logging.DEBUG)
logger.propagate = False  # Avoid duplicate logs when using multiple loggers


def config_file_handler(filename='default.log'):
    fh = logging.FileHandler(str(LOG_DIR / filename))
    fh.setFormatter(logging.Formatter('%(message)s'))
    logger.addHandler(fh)


def config_stream_handler():
    sh = logging.StreamHandler()
    sh.setFormatter(logging.Formatter('%(message)s'))
    logger.addHandler(sh)


def get_logger(filename='default.log'):
    config_file_handler(filename=filename)
    config_stream_handler()
    return logger
