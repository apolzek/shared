# Makefile

NETWORK_NAME = reverse-proxy

create-network:
	@docker network create $(NETWORK_NAME)
	@echo "Rede $(NETWORK_NAME) criada com sucesso."


.PHONY: create-network up down
