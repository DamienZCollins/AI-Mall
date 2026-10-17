-- 回补库存并移除买家标记，用于消息发送失败补偿与超时关单
--
-- KEYS[1] 库存        mall:seckill:stock:{activityId}
-- KEYS[2] 买家集合    mall:seckill:buyers:{activityId}
--
-- ARGV[1] 用户 ID，传空字符串表示只回补库存
-- ARGV[2] 活动总量上限
--
-- 返回：1 已回补；0 库存键不存在；-1 库存已达上限

local stockKey = KEYS[1]
local buyersKey = KEYS[2]
local userId = ARGV[1]
local maxStock = tonumber(ARGV[2])

if redis.call('EXISTS', stockKey) == 0 then
    return 0
end

local stock = tonumber(redis.call('GET', stockKey))
if maxStock and stock >= maxStock then
    return -1
end

redis.call('INCR', stockKey)
if userId ~= '' then
    redis.call('SREM', buyersKey, userId)
end
return 1
