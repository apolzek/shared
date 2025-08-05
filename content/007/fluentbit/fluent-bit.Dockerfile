FROM debian:bullseye-slim

# Install build dependencies
RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y \
    build-essential \
    cmake \
    curl \
    git \
    bison \
    flex \
    bash \
    netcat \
    libssl-dev \
    zlib1g-dev \
    libyaml-dev \
    ca-certificates && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /tmp

# Download Fluent Bit source
ADD https://github.com/fluent/fluent-bit/archive/refs/tags/v4.0.0.tar.gz /tmp/fluent-bit.tar.gz

# Build Fluent Bit
RUN tar -xzf fluent-bit.tar.gz && \
    cd fluent-bit-4.0.0 && \
    rm -rf build && mkdir build && cd build && \
    cmake .. && \
    make -j$(nproc) && \
    mkdir -p /fluent-bit/bin && \
    cp bin/fluent-bit /fluent-bit/bin/ && \
    mkdir -p /fluent-bit/etc && \
    cd ../.. && rm -rf fluent-bit*


# Copy configs
#COPY fluent-bit.conf /fluent-bit/etc/fluent-bit.conf
#COPY parsers.conf /fluent-bit/etc/parsers.conf
COPY fluentbit-entrypoint.sh /entrypoint.sh

RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
