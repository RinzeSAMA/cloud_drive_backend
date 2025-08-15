-- 请求体内容
wrk.method = "POST"
wrk.body = '{"password": "HYsmn9"}' -- @RequestBody 参数
wrk.headers["Content-Type"] = "application/json"

-- 中间文件路径
local log_file_path = "wrk/status_codes.log"

-- 初始化文件（在测试开始时清空文件内容）
function setup()
    local file = io.open(log_file_path, "w") -- 打开文件，写入模式清空内容
    file:close() -- 立即关闭文件
end

-- 每个请求完成后调用的函数
function response(status, headers, body)
    -- 将状态码写入中间文件
    local file = io.open(log_file_path, "a") -- 追加模式打开文件
    file:write(tostring(status) .. "\n") -- 写入状态码并换行
    file:close()
end

-- 测试结束时调用的函数
function done(summary, latency, requests)
    -- 初始化计数器
    local request_count = 0 -- 总请求数
    local status_429_count = 0 -- HTTP 429 状态码计数
    local other_status_count = 0 -- 其他状态码计数

    -- 打开文件并逐行读取状态码
    for line in io.lines(log_file_path) do
        request_count = request_count + 1
        if tonumber(line) == 429 then
            status_429_count = status_429_count + 1
        else
            other_status_count = other_status_count + 1
        end
    end

    -- 打印统计结果
    print("--------------------------------------------------")
    print("限流请求统计 (HTTP 429):")
    print("--------------------------------------------------")
    print(string.format("总请求数 (Total Requests): %d", request_count))
    print(string.format("被限流请求数 (429 Requests): %d", status_429_count))
    print(string.format("其他状态码请求数 (Other Status Codes): %d", other_status_count))

    -- 计算并打印限流请求比例
    if request_count > 0 then
        local rate_limited_percentage = (status_429_count / request_count) * 100
        print(string.format("限流请求比例 (Rate Limited Percentage): %.2f%%", rate_limited_percentage))
    else
        print("限流请求比例 (Rate Limited Percentage): 无有效请求 (N/A)")
    end
    -- 清理文件
    os.remove(log_file_path)
end
