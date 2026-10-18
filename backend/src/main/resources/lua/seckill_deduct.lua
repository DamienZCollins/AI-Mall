-- 秒杀预扣：库存判断、用户去重、扣减、记录买家，全部在一次原子操作内完成
--
-- KEYS[1] 库存        mall:seckill:stock:{activityId}
-- KEYS[2] 买家集合    mall:seckill:buyers:{activityId}
-- KEYS[3] 开始时间    mall:seckill:start:{activityId}   毫秒时间戳
-- KEYS[4] 结束时间    mall:seckill:end:{activityId}     毫秒时间戳
--
-- ARGV[1] 当前时间戳（毫秒）
-- ARGV[2] 用户 ID
--
-- 返回：1 成功；-1 未开始；-2 已结束；-3 已售罄；-4 重复下单；-5 未预热
-- 返回 -5 说明开始时间或结束时间键缺失，通常为活动未预热、键已过期或被淘汰
-- 返回负数时脚本未做任何修改，调用方无需补偿

local stockKey = KEYS[1]
local buyersKey = KEYS[2]
local startKey = KEYS[3]
local endKey = KEYS[4]

local now = tonumber(ARGV[1])
local userId = ARGV[2]

local startTime = redis.call('GET', startKey)
local endTime = redis.call('GET', endKey)
if not startTime or not endTime then
    return -5
end
if now < tonumber(startTime) then
    return -1
end
if now > tonumber(endTime) then
    return -2
end
if redis.call('SISMEMBER', buyersKey, userId) == 1 then
    return -4
end

local stock = tonumber(redis.call('GET', stockKey))
if not stock or stock <= 0 then
    return -3
end

redis.call('DECR', stockKey)
redis.call('SADD', buyersKey, userId)
return 1
