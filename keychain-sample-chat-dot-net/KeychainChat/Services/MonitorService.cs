using System;
using System.Threading.Tasks;

using NLog;

using Keychain;
using KeychainChat.Interfaces;
using NLog.Fluent;
using System.Diagnostics.Metrics;
using System.Threading;
using Windows.System;
using System.Configuration;
using System.Diagnostics;

namespace KeychainChat.Services
{
    public class MonitorService
    {
        private static string TAG = "MonitorService";
        private Logger log = LogManager.GetCurrentClassLogger();

        private MonitorThreadStatus state = MonitorThreadStatus.Stopped;
        private Mutex mutex = new Mutex();
        private Gateway gateway;
        private int refreshInterval;

        public EventHandler Refreshed;

        public MonitorService(Gateway gateway)
        {
            this.gateway = gateway;

            var interval = ConfigurationManager.AppSettings.Get("RefreshInterval");

            if (interval != null)
            {
                refreshInterval = int.Parse(interval) * 1000;
            }
            else
            {
                refreshInterval = 5000;
            }
        }

        public void AddListener(IRefreshListener listener)
        {
            Refreshed += listener.OnRefresh;
        }

        public void RemoveListener(IRefreshListener listener)
        {
            if (Refreshed != null)
            {
                Refreshed -= listener.OnRefresh;
            }
        }

        protected virtual void OnRefreshListeners()
        {
            if (Refreshed != null)
            {
                Refreshed(this, EventArgs.Empty);
            }
        }

        public void Start()
        {
            log.Debug(TAG, "Starting");
            gateway.onStart();
            gateway.onResume();

            state = MonitorThreadStatus.Started;

            Task.Run(() => runThread());
        }

        public void Stop()
        {
            log.Debug(TAG, "Stopping");

            if (gateway == null)
            {
                return;
            }

            state = MonitorThreadStatus.Stopped;

            Task.Run(() => gateway.onPause())
                .ContinueWith(task =>
                {
                    try
                    {
                        gateway.onStop();
                    }
                    catch (Exception e)
                    {
                        Debug.WriteLine("Error shutting down monitor: " + e.Message);
                    }
                });
        }

        public void Pause()
        {
            log.Debug(TAG, "Pausing");

            if (gateway == null)
            {
                return;
            }

            state = MonitorThreadStatus.Paused;

            gateway.onPause();
        }

        public void Resume()
        {
            log.Debug(TAG, "Resuming");

            if (gateway == null)
            {
                return;
            }

            state = MonitorThreadStatus.Resumed;

            gateway.onResume();
        }

        public void SetState(MonitorThreadStatus newState)
        {
            try
            {
                if (gateway == null)
                {
                    return;
                }

                lock (gateway)
                {
                    switch (newState)
                    {
                        case MonitorThreadStatus.Started:
                            Start();
                            break;

                        case MonitorThreadStatus.Resumed:
                            Resume();
                            break;

                        case MonitorThreadStatus.Paused:
                            Pause();
                            break;

                        case MonitorThreadStatus.Stopped:
                            Stop();
                            break;
                    }

                    Debug.WriteLine($"Monitor thread state set to:{newState}");
                }
            }
            catch (Exception e)
            {
                Debug.WriteLine("Error setting monitor state: ", e);
            }
        }

        private void runThread()
        {
            Debug.WriteLine("Monitor thread running");

            if (!mutex.WaitOne(0))
            {
                Debug.WriteLine("Monitor thread already running.");
                return;
            }

            try
            {
                while (state != MonitorThreadStatus.Stopped)
                {

                    if (state == MonitorThreadStatus.Paused)
                    {
                        Thread.Sleep(1000);
                        continue;
                    }

                    OnRefreshListeners();

                    Thread.Sleep(refreshInterval);
                }
            }
            finally
            {
                mutex.ReleaseMutex();
            }
        }
    }
}
