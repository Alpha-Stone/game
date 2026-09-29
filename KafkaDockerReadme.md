A .tar file is usually an image exported with docker save. You load it with docker load, then run it like any other image. No internet is needed once the file is on the machine.

1. Load the image

bash
docker load -i kafka.tar

It prints the image name and tag, e.g. Loaded image: apache/kafka:3.7.0. You can also check with:

bash
docker images

If instead the tar came from docker export (a container filesystem dump), you'd use docker import kafka.tar kafka:local, but that loses the entrypoint/CMD metadata, so you'd have to specify the start command yourself. Most Kafka tars are docker save output.

2. Run it (KRaft mode, no Zookeeper)

For the official apache/kafka image:

bash
docker run -d --name kafka -p 9092:9092 apache/kafka:3.7.0

That works out of the box with defaults, reachable at localhost:9092.

For a Bitnami image:

bash
docker run -d --name kafka -p 9092:9092 \
-e KAFKA_CFG_NODE_ID=0 \
-e KAFKA_CFG_PROCESS_ROLES=controller,broker \
-e KAFKA_CFG_CONTROLLER_QUORUM_VOTERS=0@localhost:9093 \
-e KAFKA_CFG_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 \
-e KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
-e KAFKA_CFG_CONTROLLER_LISTENER_NAMES=CONTROLLER \
-e KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT \
bitnami/kafka:3.7

3. If your image is an older Zookeeper-based one (e.g. wurstmeister/kafka, confluentinc/cp-kafka), you also need a Zookeeper image saved as a tar and loaded the same way. Then use a compose file or a shared network:

bash
docker network create kafka-net
docker run -d --name zookeeper --network kafka-net -p 2181:2181 zookeeper:3.8
docker run -d --name kafka --network kafka-net -p 9092:9092 \
-e KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181 \
-e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
-e KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:9092 \
-e KAFKA_OFFSET_TOPIC_REPLICATION_FACTOR=1 \
<kafka-image>

4. Verify it works

bash
docker logs -f kafka
docker exec -it kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic test
docker exec -it kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list

The script path varies by image. Bitnami uses /opt/bitnami/kafka/bin/, Confluent uses plain kafka-topics on the PATH.

Offline gotchas

The most common problem is ADVERTISED_LISTENERS. If clients connect from outside the container (e.g. your Spring Boot app on the host), it must be an address they can reach, such as localhost:9092 or the host's IP. If the app runs in another container on the same Docker network, use kafka:9092 instead.
If the image name and tag in docker images show <none>, tag it yourself: docker tag <IMAGE_ID> kafka:local.
If you create the tar yourself on a connected machine, use docker save -o kafka.tar apache/kafka:3.7.0, and save every image you need (Kafka, Zookeeper, Kafka UI, etc.).
Docker won't try to pull if the image already exists locally with the exact name and tag you reference, so use the name printed by docker load.

If you tell me which image the tar contains (docker load output or tar -tf kafka.tar | head), I can give you the exact run command or a docker-compose file for it.