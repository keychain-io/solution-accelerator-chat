using System;
using System.Collections.Generic;
using System.Linq;
using System.Configuration;

using NLog;

using Keychain;
using KeychainChat.Interfaces;
using Uri = Keychain.Uri;
using Contact = Keychain.Contact;
using KeychainChat.Utils;
using System.Diagnostics;
using System.Text;

namespace KeychainChat.Services
{
    public class KeychainService 
    {
        private static string TAG = "KeychainService";

        private Logger log = LogManager.GetCurrentClassLogger();

        private readonly string configFile;
        private readonly string dropSqlFile;
        private readonly string createSqlFile;
        private readonly string dbPath;

        public static string ERROR_DECRYPTING_MESSAGE = "Error decrypting message.";

        private static KeychainService instance;

        private static Gateway gateway;
        private static MonitorService monitorService;

        private static readonly object lockObj = new Object();

        public static KeychainService GetInstance()
        {
            lock(lockObj)
            {
                if (instance == null)
                {
                    instance = new KeychainService();
                }
            }

            return instance;
        }

        private KeychainService()
        {
            configFile = ConfigurationManager.AppSettings.Get("KeychainConfigFile");
            dropSqlFile = ConfigurationManager.AppSettings.Get("KeychainDropSqlFile");
            createSqlFile = ConfigurationManager.AppSettings.Get("KeychainCreateSqlFile");
            dbPath = ConfigurationManager.AppSettings.Get("DBPath");

            gateway = new Gateway(dbPath, configFile, dropSqlFile, createSqlFile, false);

            // ViewModels that need to be notified of updates will add/remove themselves
            // from the service ass needd
            monitorService = new MonitorService(gateway);
            string address;
            string[] mnemonics;
            Debug.WriteLine("Seeding database");
            int rc = gateway.seed(out address, out mnemonics);

            Debug.WriteLine($"Seeding database returned {rc}");
        }

        public void RegisterRefreshListener(IRefreshListener refreshable)
        {
            monitorService.AddListener(refreshable);
        }

        public void UnregisterRefreshListener(IRefreshListener refreshable)
        {
            monitorService.RemoveListener(refreshable);
        }

        public IList<Facade> GetPersonas()
        {
            try
            {
                Persona[] personas;
                int rc = gateway.getPersonas(out personas);
                return personas;
            }
            catch (Exception e)
            {
                Debug.WriteLine("Error getting personas: ", e);
                return new List<Facade>();
            }
        }

        public bool SetActivePersona(Persona persona)
        {
            return gateway.setActivePersona(persona) == 0;
        }

        public Contact FindContact(string uri)
        {
            try
            {
                if (string.IsNullOrEmpty(uri))
                {
                    Debug.WriteLine("Error checking contacts for existence: uri is null");
                }

                gateway.getContacts(out Contact[] contacts);

                foreach (Contact contact in contacts)
                {
                    if (uri.Equals(StringUtils.GetUriString(contact.getUri())))
                    {
                        return contact;
                    }
                }
            }
            catch (Exception e)
            {
                Debug.WriteLine("Error checking contacts for existence: ", e);
            }

            return null;
        }

        public Contact? FindContact(string firstName, string lastName, string uri)
        {
            try
            {
                if (firstName == null || lastName == null)
                {
                    Debug.WriteLine("Error checking contacts for existence: One or both names are null");
                }

                gateway.getContacts(out Contact[] contacts);

                foreach (Contact contact in contacts)
                {
                    if (firstName.Equals(contact.getName()) && lastName.Equals(contact.getSubName()))
                    {
                        return contact;
                    }

                    if (!string.IsNullOrEmpty(uri) && uri.Equals(StringUtils.GetUriString(contact.getUri())))
                    {
                        return contact;
                    }
                }
            }
            catch (Exception e)
            {
                Debug.WriteLine("Error checking contacts for existence: ", e);
            }

            return null;
        }

        public Persona GetActivePersona()
        {
            try
            {
                gateway.getActivePersona(out Persona persona);
                return persona;
            }
            catch (Exception)
            {
                // Don't log this -- Debug.WriteLine("Error getting active Persona: ", e);
            }

            return null;
        }

        public Persona CreatePersona(string firstName, string lastName, SecurityLevel level)
        {
            try
            {
                int rc = gateway.createPersona(out Persona persona, firstName, lastName, level);
                return persona;
            }
            catch (Exception e)
            {
                Debug.WriteLine("Error creating persona: ", e);
            }

            return null;
        }

        public void DeletePersona(Persona persona)
        {
            if (persona == null)
            {
                Debug.WriteLine("Error deleting persona. Persona is null");
                return;
            }

            gateway.deleteFacade(persona);
        }

        public Contact CreateContact(string firstName, string lastName, Uri uri)
        {
            try
            {
                string? uriString = StringUtils.GetUriString(uri);

                if (uriString == null) {
                    throw new ArgumentException(TAG, "uri");
                }

                var contact = FindContact(uriString);

                if (contact == null)
                {
                    gateway.createContact(out contact, firstName, lastName, uri);
                }

                return contact;
            }
            catch (Exception e)
            {
                Debug.WriteLine("Error creating contact: ", e);
            }

            return null;
        }

        public IList<Facade> GetContacts()
        {
            gateway.getContacts(out Contact[] contacts);
            return contacts;
        }

        public void DeleteContact(Contact contact)
        {
            if (contact == null)
            {
                Debug.WriteLine("Error deleting contact. Contact is null");
                return;
            }

            gateway.deleteFacade(contact);
        }

        public void ModifyContact(Contact contact, string firstName, string lastName)
        {
            if (contact == null)
            {
                Debug.WriteLine("Error modifying contact. Contact is null");
                return;
            }

            gateway.renameFacade(contact, firstName, lastName);
        }

        public void ModifyPersona(Persona persona, string firstName, string lastName)
        {
            if (persona == null)
            {
                Debug.WriteLine("Error modifying persona. Contact is null");
                return;
            }

            gateway.renameFacade(persona, firstName, lastName);
        }


        public void StopMonitor() 
        { 
            if (monitorService != null) 
                monitorService.Stop(); 
        }
        public void StartMonitor() 
        { 
            if (monitorService != null) 
                monitorService.Start(); 
        }

        public string? SignThenEncrypt(IList<Contact> contacts, string msg)
        {
            try
            {
                var utf8Data = Encoding.UTF8.GetBytes(msg);
                gateway.signThenEncrypt(out string cipherText,
                                        utf8Data,
                                        contacts.ToArray());
                return cipherText;
            }
            catch (Exception e)
            {
                Debug.WriteLine("Error in SignThenEncrypt", e);
            }

            return null;
        }

        public string DecryptThenVerify(string cipherText)
        {
            try
            {
                Debug.WriteLine("decryptThenVerify: " + cipherText);

                var rc = gateway.decryptThenVerify(out List<Verification> verificationList,
                                                   out byte[] decrypted,
                                                   cipherText);

                if (rc != 0)
                {
                    Debug.WriteLine($"decryptThenVerify returned {rc} for the following cypherText");
                    Debug.WriteLine($"{cipherText}");
                }

                string decryptedString = Encoding.UTF8.GetString(decrypted);

                return decryptedString;
            }
            catch (Exception e)
            {
                Debug.WriteLine(ERROR_DECRYPTING_MESSAGE, e);
                return "";
            }
        }

        public void AddListener(IRefreshListener listener)
        {
            monitorService.AddListener(listener);
        }
    }
}
