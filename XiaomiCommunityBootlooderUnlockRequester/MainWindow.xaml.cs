using System;
using System.Diagnostics;
using System.Net;
using System.Net.Http;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using System.Windows;

namespace XiaomiCommunityBootlooderUnlockRequester
{
    public partial class MainWindow : Window
    {
        private static HttpClient client;
        private CancellationTokenSource cts;
        private bool isRunning = false;

        private const string TargetUrl = "https://sgp-api.buy.mi.com/bbs/api/global/apply/bl-auth";
        private const string SavedCookie = "new_bbs_serviceToken=0WRYJGGvRNUugSPo%2FM2ZjSmoWKrZ4KjAKMyiC%2F3P3NXK6%2FgKGXsrwUA2vFCK8ABI9DQyLyisYdz%2FrMN0gBw0uNbnkYObEjvSvPrp3XZGcSaBSlNImiUTK62%2BybT7p87b7cbHmuwVw%2F5rD%2BFFng6Q0LvDOgrb66D8RPmREUsII9U%3D;versionCode=500438;versionName=5.4.38;deviceId=D939328F9A3DCFDAEC43FB55CEE7628C66940D86;";
        private const string SavedUserAgent = "okhttp/4.12.0";

        public MainWindow()
        {
            InitializeComponent();
            InitializeHttpClientWithProxy();
        }

        private void InitializeHttpClientWithProxy()
        {
            // The conditional compilation directive automatically switches modes
#if DEBUG
    // ACTIVE ONLY IN TESTING: Routes traffic cleanly through HTTP Toolkit local proxy port
    var handler = new HttpClientHandler
    {
        Proxy = new WebProxy("http://127.0.0.1:8000", false),
        UseProxy = true,
        ServerCertificateCustomValidationCallback = (sender, cert, chain, sslPolicyErrors) => true
    };
    client = new HttpClient(handler);
    
    // Quick console alert trace to identify environment status
    Debug.WriteLine("📡 Debug Environment Mode Active: Traffic routed through HTTP Toolkit Proxy.");
#else
            // ACTIVE FOR THE LIVE RUN: Direct, ultra-low latency connection bypassing all proxies
            var directHandler = new HttpClientHandler
            {
                UseProxy = false, // Bypasses the local network adapters entirely
                AutomaticDecompression = DecompressionMethods.GZip | DecompressionMethods.Deflate
            };
            client = new HttpClient(directHandler);
#endif
        }

        private async void BtnStart_Click(object sender, RoutedEventArgs e)
        {
            if (isRunning) return;

            // Input validations to load time inputs securely from UI fields
            if (!int.TryParse(txtHour.Text, out int h) ||
                !int.TryParse(txtMin.Text, out int m) ||
                !int.TryParse(txtSec.Text, out int s) ||
                !int.TryParse(txtMs.Text, out int ms))
            {
                MessageBox.Show("Please enter valid numeric time parameters!", "Input Error", MessageBoxButton.OK, MessageBoxImage.Warning);
                return;
            }

            isRunning = true;
            cts = new CancellationTokenSource();

            txtLog.Text = $"⏰ Engine Armed. Awaiting target window frame -> {h:D2}:{m:D2}:{s:D2}.{ms:D3} ...\n";

            await Task.Run(() => ScheduleLoop(h, m, s, ms, cts.Token));
        }

        private void BtnStop_Click(object sender, RoutedEventArgs e)
        {
            if (!isRunning) return;
            cts?.Cancel();
            isRunning = false;
            AppendLog("🛑 Sequence stopped manually by user.");
        }

        private async Task ScheduleLoop(int targetHour, int targetMin, int targetSec, int targetMs, CancellationToken token)
        {
            try
            {
                while (!token.IsCancellationRequested)
                {
                    DateTime now = DateTime.Now;

                    // Dynamically check against the target scheduler inputs you write in the boxes
                    if (now.Hour == targetHour && now.Minute == targetMin && now.Second == targetSec && now.Millisecond >= targetMs)
                    {
                        AppendLog("🚀 TRIGGER FREQUENCY MATCHED! Executing network payload array...");
                        await ExecuteUnlockBurst(token);
                        break;
                    }

                    await Task.Delay(5, token); // Tighter time tracking loop
                }
            }
            catch (OperationCanceledException)
            {
                // SAFE EXIT: This catches the TaskCanceledException when you click "Disarm Engine"
                // It suppresses the red screen crash error and allows the thread to close cleanly.
                AppendLog("🔌 Background scheduler thread dismantled safely.");
            }
            catch (Exception ex)
            {
                AppendLog($"⚠️ Unexpected scheduler exception caught: {ex.Message}");
            }
        }

        private async Task ExecuteUnlockBurst(CancellationToken token)
        {
            var requestMessage = new HttpRequestMessage(HttpMethod.Post, TargetUrl);
            requestMessage.Headers.Add("Accept", "application/json");
            requestMessage.Headers.Add("Connection", "Keep-Alive");
            requestMessage.Headers.Add("User-Agent", SavedUserAgent);
            requestMessage.Headers.Add("Cookie", SavedCookie);

            var jsonPayload = new { is_retry = false };
            string jsonString = JsonSerializer.Serialize(jsonPayload);

            long endTime = DateTime.Now.Ticks + (60 * TimeSpan.TicksPerSecond);
            int requestCount = 0;

            while (DateTime.Now.Ticks < endTime && !token.IsCancellationRequested)
            {
                requestCount++;
                try
                {
                    var currentRequest = CloneRequest(requestMessage, jsonString);
                    var stopwatch = Stopwatch.StartNew();
                    var response = await client.SendAsync(currentRequest, token);
                    stopwatch.Stop();

                    if (response.IsSuccessStatusCode)
                    {
                        string responseBody = await response.Content.ReadAsStringAsync();
                        AppendLog($"[#{requestCount}] 200 OK | Latency: {stopwatch.ElapsedMilliseconds}ms");

                        using (JsonDocument doc = JsonDocument.Parse(responseBody))
                        {
                            JsonElement root = doc.RootElement;
                            if (root.TryGetProperty("data", out JsonElement dataNode))
                            {
                                int applyResult = dataNode.GetProperty("apply_result").GetInt32();
                                string deadlineFormat = dataNode.GetProperty("deadline_format").GetString();

                                if (applyResult == 3)
                                {
                                    AppendLog($"❌ Result 3: Quota Full. Frame: {deadlineFormat}");
                                    if (deadlineFormat.Contains("10/10"))
                                    {
                                        AppendLog("🛑 Server Rollover Locked (10/10). Exiting execution sequence cleanly.");
                                        isRunning = false;
                                        return;
                                    }
                                }
                                else if (applyResult == 6)
                                {
                                    AppendLog("⏳ Result 6: Evaluating submission parameters...");
                                }
                                else
                                {
                                    AppendLog($"🎉 WIN PAYLOAD TRIGGERED: {responseBody}");
                                    MessageBox.Show($"WINNER! Code: {applyResult}\nPayload: {responseBody}", "Xiaomi Unlock Success!", MessageBoxButton.OK, MessageBoxImage.Information);
                                    isRunning = false;
                                    return;
                                }
                            }
                        }
                    }
                    else
                    {
                        AppendLog($"⚠️ Error code returned: {response.StatusCode}");
                    }
                }
                catch (Exception ex)
                {
                    AppendLog($"⚠️ Request execution drop: {ex.Message}");
                }

                await Task.Delay(30, token);
            }

            isRunning = false;
            AppendLog("🛑 Burst window closed.");
        }

        private HttpRequestMessage CloneRequest(HttpRequestMessage req, string jsonContent)
        {
            var clone = new HttpRequestMessage(req.Method, req.RequestUri);
            foreach (var header in req.Headers) clone.Headers.TryAddWithoutValidation(header.Key, header.Value);
            clone.Content = new StringContent(jsonContent, Encoding.UTF8, "application/json");
            return clone;
        }

        private void AppendLog(string message)
        {
            Dispatcher.Invoke(() =>
            {
                txtLog.AppendText($"[{DateTime.Now:HH:mm:ss.fff}] {message}\n");
                txtLog.ScrollToEnd();
            });
        }
    }
}
