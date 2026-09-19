local messages = {}
local errors = {}
live_timers = 0
mp = {
    register_script_message = function(name, handler) messages[name] = handler end,
    msg = { error = function(message) errors[#errors + 1] = message end },
}

assert(loadfile(arg[1]))()
local dispatch = assert(messages["webhtv-custom-button"])
assert(startup_count == 1 and toggle_state == true and live_timers == 1)
assert(hidden_count == 1)
assert(click_count == nil and long_count == nil)
assert(return_count == 1)
assert(disabled_legacy_ran == nil)
assert(#errors == 1 and errors[1]:find("expected startup error", 1, true))
for _, trigger in ipairs({"click", "long", "startup"}) do
    dispatch("disabled_script_" .. trigger, "short")
    dispatch("disabled_script_" .. trigger, "long")
end
assert(#errors == 1 and live_timers == 1 and startup_count == 1)

dispatch("startup", "short")
assert(startup_count == 2 and toggle_state == false and live_timers == 0)
dispatch("startup", "short")
assert(startup_count == 3 and toggle_state == true and live_timers == 1)
dispatch("startup", "long")
dispatch("hidden", "short")
dispatch("hidden", "long")
dispatch("missing", "short")
assert(startup_count == 3 and hidden_count == 1 and live_timers == 1)

dispatch("click", "long")
dispatch("hold", "short")
assert(click_count == nil and long_count == nil)
dispatch("click", "short")
dispatch("hold", "long")
assert(click_count == 1 and long_count == 1)

dispatch("legacy", "short")
assert(legacy_count == 7)
dispatch("legacy", "short")
dispatch("legacy", "long")
assert(legacy_count == 8 and legacy_long == 9)
dispatch("broken", "short")
dispatch("click", "short")
assert(#errors == 2 and click_count == 2)
print("PASS: startup/click/long/hidden/legacy/error isolation")
