-- 계정별 요청 한도(GCRA). ADR user_request_limit/00 "Redis 키와 스크립트".
-- KEYS[1] = 계정·한도의 TAT 키, KEYS[2] = 탐침 키
-- ARGV = 지금(ms), T(ms), tau(ms), 탐침 여부('1'/'0'). 모두 문자열이다.
-- 반환: -1 = 받음, 0 이상 = 거절이며 1건이 돌아올 때까지 남은 ms.
-- shebang(#!lua)을 두지 않는다. 두면 Redis가 maxmemory에 닿았을 때 거절 경로까지 OOM 오류가 된다.
local now, t, tau = tonumber(ARGV[1]), tonumber(ARGV[2]), tonumber(ARGV[3])
if ARGV[4] == '1' then redis.call('SET', KEYS[2], now, 'PX', 10000) end
local tat = tonumber(redis.call('GET', KEYS[1]))
if tat == nil or tat < now then tat = now end
local allowAt = tat - tau
if now < allowAt then return allowAt - now end
local newTat = tat + t
redis.call('SET', KEYS[1], newTat, 'PX', newTat - now)
return -1
