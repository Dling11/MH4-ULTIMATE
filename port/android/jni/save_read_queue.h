#pragma once

#include <functional>
#include <future>
#include <memory>
#include <mutex>
#include <queue>

namespace Mh4uAndroid {

// Read/copy callbacks execute between CPU loops, never during guest save writes.
// Closing the core releases waiting callers instead of leaving a queued JNI reference alive.
class SaveReadQueue {
    struct Job {
        std::function<void()> action;
        std::shared_ptr<std::promise<bool>> result;
    };
    std::mutex mutex;
    std::queue<Job> pending;
    bool accepting = false;

public:
    void Open() {
        std::scoped_lock lock(mutex);
        accepting = true;
    }
    std::future<bool> Submit(std::function<void()> action) {
        auto result = std::make_shared<std::promise<bool>>();
        auto future = result->get_future();
        std::scoped_lock lock(mutex);
        if (accepting) pending.push({std::move(action), result});
        else result->set_value(false);
        return future;
    }
    bool HasPending() {
        std::scoped_lock lock(mutex);
        return !pending.empty();
    }
    void RunPending(bool running) {
        std::queue<Job> jobs;
        {
            std::scoped_lock lock(mutex);
            jobs.swap(pending);
        }
        for (; !jobs.empty(); jobs.pop()) {
            auto& job = jobs.front();
            try {
                if (running) job.action();
                job.result->set_value(running);
            } catch (...) { job.result->set_exception(std::current_exception()); }
        }
    }
    void Close() {
        std::queue<Job> jobs;
        {
            std::scoped_lock lock(mutex);
            accepting = false;
            jobs.swap(pending);
        }
        for (; !jobs.empty(); jobs.pop()) jobs.front().result->set_value(false);
    }
};
} // namespace Mh4uAndroid
