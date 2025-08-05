import time

from logger import get_logger
from utils import parse_log_with_datetime, delete_log

logger = get_logger()


class LogSim:
    def __init__(self, src_path='log_file.log'):
        with open(src_path, 'r', encoding='utf-8', errors='ignore') as f:
            self.log_lines = f.readlines()

        self.log_data = [line.strip() for line in self.log_lines]
        # self.log_data = parse_log_with_datetime(self.log_lines)

    def stream(self, speed=1.0, delay_epsilon=1.0):
        for i in range(len(self.log_data)):
            logger.info(self.log_data[i])
            time.sleep(delay_epsilon / speed)


if __name__ == "__main__":
    # sim = LogSim(src_path='./data/BGL/BGL_test.log')
    sim = LogSim(src_path='./logs/default.log')

    sim.stream(speed=2, delay_epsilon=0.5)
