#!/bin/sh
# 建 RocketMQ 主题。
#
# RocketMQ 5.x 的主题需要显式声明能接受的消息类型，否则发送时会报
# "Current message type not match with topic accept message types"。
# 普通消息与延迟消息不能放同一主题（MIXED 类型 broker 不支持），故拆成两个。
# 主题只创建一次即可，updateTopic 是幂等的。
#
# 用法：sh scripts/init-rocketmq-topics.sh

set -e

CONTAINER=mall-rocketmq-broker
ROCKETMQ_HOME=/home/rocketmq/rocketmq-5.3.2
BROKER=127.0.0.1:10911

create_topic() {
    docker exec "$CONTAINER" sh -c "cd $ROCKETMQ_HOME && sh bin/mqadmin updateTopic \
        -b $BROKER -t $1 -r 4 -w 4 -a '+message.type=$2'"
}

create_topic mall-seckill-order NORMAL
create_topic mall-seckill-timeout DELAY

echo "主题创建完成：mall-seckill-order(NORMAL) / mall-seckill-timeout(DELAY)"
echo "注意：代理会缓存主题路由，新建主题后需要重启代理：docker compose restart rocketmq-proxy"
