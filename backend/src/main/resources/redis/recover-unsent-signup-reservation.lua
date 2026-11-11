local function declaredKeys(keys)
    local declared = {}
    for _, key in ipairs(keys) do declared[key] = key end
    return declared
end

local function recoverUnsentReservation(raw, declared, now)
    local reservation = cjson.decode(raw)
    local reservationKey = assert(declared[reservation.key])
    local pendingKey = assert(declared[reservation.pending])
    for _, counter in ipairs(reservation.counters) do assert(declared[counter]) end
    for _, index in ipairs(reservation.indexes) do assert(declared[index]) end
    if reservation.deliveryKey then assert(declared[reservation.deliveryKey]) end
    if reservation.recoverAt > now then return 0 end

    -- Starting delivery replaces this value, so only the original value can be refunded here.
    local recovered = 0
    if redis.call('GET', reservationKey) == raw then
        redis.call('DEL', reservationKey)
        if reservation.deliveryKey and redis.call('GET', reservation.deliveryKey) == reservationKey then
            redis.call('DEL', reservation.deliveryKey)
        end
        for _, counter in ipairs(reservation.counters) do
            local key = declared[counter]
            if tonumber(redis.call('GET', key) or '0') > 0 then redis.call('DECR', key) end
        end
        if redis.call('HGET', pendingKey, 'generationId') == reservation.generation then
            redis.call('DEL', pendingKey)
        end
        recovered = 1
    end
    for _, index in ipairs(reservation.indexes) do redis.call('ZREM', declared[index], raw) end
    return recovered
end
