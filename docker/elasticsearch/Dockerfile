FROM docker.elastic.co/elasticsearch/elasticsearch:8.17.6

# ik 中文分词插件，用于商品名称与描述的分词
RUN bin/elasticsearch-plugin install --batch \
    https://release.infinilabs.com/analysis-ik/stable/elasticsearch-analysis-ik-8.17.6.zip
