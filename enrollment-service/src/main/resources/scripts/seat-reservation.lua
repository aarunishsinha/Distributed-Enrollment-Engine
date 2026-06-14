-- Atomic seat reservation Lua script
-- Ported from the original TypeScript implementation
--
-- KEYS[1] = course:{courseId}:seats   (String: available seat counter)
-- KEYS[2] = course:{courseId}:users   (Set: enrolled user IDs)
-- ARGV[1] = userId
--
-- Returns:
--   0 = Success (seat reserved, counter decremented, user added to set)
--   1 = User already enrolled in this course
--   2 = Course is full (no seats remaining)

local course_seats_key = KEYS[1]
local course_users_key = KEYS[2]
local user_id = ARGV[1]

-- Check if user is already enrolled
if redis.call("SISMEMBER", course_users_key, user_id) == 1 then
    return 1
end

-- Check seat availability
local seats = tonumber(redis.call("GET", course_seats_key) or "0")
if seats <= 0 then
    return 2
end

-- Reserve seat atomically: decrement counter + add user to set
redis.call("DECR", course_seats_key)
redis.call("SADD", course_users_key, user_id)
return 0
