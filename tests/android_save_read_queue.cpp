#include "../port/android/jni/save_read_queue.h"
#include <atomic>
#include <cassert>
#include <condition_variable>
#include <iostream>
#include <stdexcept>
#include <thread>

int main() {
    using Mh4uAndroid::SaveReadQueue;
    {
        SaveReadQueue queue;
        int calls = 0;
        assert(!queue.Submit([&] { ++calls; }).get());
        queue.Open();
        auto pending = queue.Submit([&] { ++calls; });
        queue.Close();
        assert(!pending.get() && calls == 0);
        queue.Open();
        queue.RunPending(true);
        assert(calls == 0); // no stale work from the previous game
    }
    {
        SaveReadQueue queue; queue.Open();
        std::mutex paused;
        std::condition_variable wake;
        bool stop = false;
        std::thread::id worker_id, callback_id;
        std::thread worker([&] {
            worker_id = std::this_thread::get_id();
            std::unique_lock lock(paused);
            while (!stop) {
                wake.wait(lock, [&] { return stop || queue.HasPending(); });
                lock.unlock(); queue.RunPending(!stop); lock.lock();
            }
            queue.Close();
        });
        std::future<bool> read;
        {
            std::scoped_lock lock(paused);
            read = queue.Submit([&] { callback_id = std::this_thread::get_id(); });
        }
        wake.notify_all();
        assert(read.get());
        assert(callback_id == worker_id && callback_id != std::this_thread::get_id());
        { std::scoped_lock lock(paused); stop = true; }
        wake.notify_all(); worker.join();
    }
    {
        SaveReadQueue queue; queue.Open();
        auto bad = queue.Submit([] { throw std::runtime_error("read failed"); });
        auto good = queue.Submit([] {});
        queue.RunPending(true);
        bool rejected = false;
        try { bad.get(); } catch (const std::runtime_error&) { rejected = true; }
        assert(rejected && good.get());
        queue.Close();
    }
    {
        SaveReadQueue queue; queue.Open();
        std::atomic<int> calls = 0;
        auto canceled = queue.Submit([&] { ++calls; });
        queue.RunPending(false);
        assert(!canceled.get() && calls == 0);
        queue.Close();
    }
    std::cout << "4 saved-file scheduling checks passed\n";
}
