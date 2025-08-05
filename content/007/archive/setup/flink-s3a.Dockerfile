FROM flink:1.17

ENV FLINK_VERSION=1.17.1
ENV HADOOP_VERSION=3.3.6
ENV AWS_SDK_VERSION=1.12.262
ENV FLINK_PLUGINS_DIR=/opt/flink/plugins

# Install required JARs for Flink to use s3a:// filesystem (MinIO or AWS S3)
RUN mkdir -p /opt/flink/plugins/s3 && \
    curl -fsSL https://repo1.maven.org/maven2/org/apache/hadoop/hadoop-aws/${HADOOP_VERSION}/hadoop-aws-${HADOOP_VERSION}.jar \
      -o /opt/flink/plugins/s3/hadoop-aws-${HADOOP_VERSION}.jar && \
    curl -fsSL https://repo1.maven.org/maven2/com/amazonaws/aws-java-sdk-bundle/${AWS_SDK_VERSION}/aws-java-sdk-bundle-${AWS_SDK_VERSION}.jar \
      -o /opt/flink/plugins/s3/aws-java-sdk-bundle-${AWS_SDK_VERSION}.jar && \
    curl -fsSL https://repo1.maven.org/maven2/org/apache/flink/flink-s3-fs-hadoop/${FLINK_VERSION}/flink-s3-fs-hadoop-${FLINK_VERSION}.jar \
      -o /opt/flink/plugins/s3/flink-s3-fs-hadoop-${FLINK_VERSION}.jar && \
    ls -lh /opt/flink/plugins/s3
