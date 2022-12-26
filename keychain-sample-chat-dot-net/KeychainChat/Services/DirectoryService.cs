using Newtonsoft.Json;
using NLog;
using System;
using System.Collections.Generic;
using System.Configuration;
using System.Net;

using KeychainChat.Common;
using System.Diagnostics;

namespace KeychainChat.Services
{
    public class DirectoryService
    {
        private static string TAG = "DirectoryService";

        static readonly Logger log = LogManager.GetCurrentClassLogger();

        private readonly string host;
        private readonly int port;
        private readonly string domain;

        public string DirectoryUri
        {
            get
            {
                return $"http://{host}:{port}/adsimulator/";
            }
        }

        public DirectoryService()
        {
            try
            {
                host = ConfigurationManager.AppSettings.Get("DirectoryHost");
                port = int.Parse(ConfigurationManager.AppSettings.Get("DirectoryPort"));
                domain = ConfigurationManager.AppSettings.Get("DirectoryDomainPrefix");
                domain += ConfigurationManager.AppSettings.Get("PairingDomain");
            }
            catch (Exception e)
            {
                Debug.WriteLine("Error creating DirectoryService: ", e);
                throw;
            }
        }

        public void UploadUri(string personaUrl)
        {
            Debug.WriteLine($"Uploading uri to trusted directory: {personaUrl}");

            try
            {
                string[] uriParts = personaUrl.Split(';');
                string encr_url = uriParts[0];
                string sign_url = uriParts[1];

                string[] encrParts = encr_url.Split(':');
                string encr_txid = encrParts[0];
                int encr_vout = int.Parse(encrParts[1]);

                string[] signParts = sign_url.Split(':');
                string sign_txid = signParts[0];
                int sign_vout = int.Parse(signParts[1]);

                using var client = new WebClient();

                client.Headers.Add(HttpRequestHeader.ContentType, "application/json");
                string url = $"{DirectoryUri}uploaduri/{domain}/{encr_txid}/{encr_vout}/{sign_txid}/{sign_vout}";

                log.Debug($"Sending request to upload URI to the directory. URL: {url}");

                string response = client.DownloadString(new System.Uri(url));

                log.Debug("Got response from Trusted Directory: " + response);

                var jsonObject = JsonConvert.DeserializeObject<Dictionary<string, string>>(response);

                if (jsonObject == null)
                {
                    throw new Exception($"Error deserializing response to upload uri to Trusted directory: {url}");
                }

                string return_string = jsonObject["response_code"];

                if (return_string == "OK")
                {
                    Debug.WriteLine($"Blockchain id upload OK: {url}");
                }
                else if (return_string == "T003_PROFILE_EXISTS")
                {
                    Debug.WriteLine($"Blockchain id already uploaded: {url}");
                }
                else
                {
                    Debug.WriteLine($"Blockchain id upload failed with error: {return_string}");
                }
            }
            catch (FormatException e)
            {
                object[] args = { personaUrl };
                log.Fatal(e, "Failed to parse the URL of the found persona. Exiting setup. " + e.Message, args);
            }
            catch (Exception e)
            {
                log.Error(e, "Error while sending request to AD simulator: " + e.Message);
            }
        }

        public IList<string>? DownloadUris()
        {
            IList<string> uriStrings = new List<string>();

            using var client = new WebClient();

            DirectoryGetResponse? getResponse;

            try
            {
                log.Debug("Looking up blockchain ids for domain: " + domain);

                string response = client.DownloadString(new System.Uri($"{DirectoryUri}getalluri/{domain}"));
                getResponse = JsonConvert.DeserializeObject<DirectoryGetResponse>(response);

                if (getResponse == null)
                {
                    throw new Exception(Constants.ERROR_DOWNLOADING_URIS);
                }
            }
            catch (Exception e)
            {
                Debug.WriteLine(Constants.ERROR_DOWNLOADING_URIS, e);
                return null;
            }

            if (getResponse.response_code != "OK")
            {
                Debug.WriteLine($"Blockchain id lookup for domain: {domain} failed with response: {getResponse.response_code}");
                return null;
            }

            // loop for each found Uri
            foreach (DirectoryEntry entry in getResponse.results)
            {
                {
                    string uriString =
                        $"{entry.encr_txid}:{entry.encr_vout};{entry.sign_txid}:{entry.sign_vout}";

                    log.Debug("Retrieved URI string from directory: " + uriString);

                    uriStrings.Add(uriString);
                }
            }

            return uriStrings;
        }
    }
}