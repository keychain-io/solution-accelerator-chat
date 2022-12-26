using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Input;
using System.Text;

using NLog;
using Keychain;
using KeychainChat.Common;
using KeychainChat.Core;
using KeychainChat.Interfaces;
using KeychainChat.MVVM.Model.Chat;
using KeychainChat.MVVM.Model.Contact;
using KeychainChat.MVVM.Model.Message;
using KeychainChat.MVVM.Model.Personas;
using KeychainChat.MVVM.Model.PlatformUser;
using KeychainChat.Services;
using KeychainChat.Services.Database;
using KeychainChat.Utils;
using KeychainChat.Services.Mqtt;
using System.Configuration;
using Newtonsoft.Json;
using KeychainChat.MVVM.Model;
using KeychainChat.Services.Channel;
using Uri = Keychain.Uri;
using ChatMessage = KeychainChat.MVVM.Model.Chat.ChatMessage;
using User = KeychainChat.MVVM.Model.Chat.User;
using System.Diagnostics;
using System.Windows.Controls;
using System.Threading;
using Exception = System.Exception;
using System.Linq;

namespace KeychainChat.MVVM.ViewModel
{
    public class KeychainViewModel :
        ObservableObject,
        IRefreshListener,
        IMqttMessageHandler,
        IStatusListener
    {
        private const string SENDING_PAIR_RESPONSE = "Sending pair response";
        private const string SAVED_PAIRED_CONTACT_TO_CHAT_DB = "Successfully saved paired contact to chat user table: ";
        private const string ERROR_CONVERTING_PAIRING_ACK_TO_JSON = "Error converting pairing ack to JSON.";
        private const string SENDING_PAIR_ACK = "Sending pair ack";
        private const string RECEIVED_CHAT_MESSAGE = "Receiving chat message:";
        private const string ERROR_PARSING_CHAT_MESSAGE = "Unable to parse chat message";
        private const string UNRECOGNIZED_SENDER = "Message sender is not in my contact list. Ignoring message.";
        private const string ERROR_SAVING_CHAT = "Error saving or updating chat from sender: ";
        private const string CHAT_SAVED_OR_UPDATED = "Chat saved or updated to chat database, recordId: ";
        private const string RECEIVED_EMPTY_MESSAGE = "No message received in chat message from: ";
        private static string ALL = Constants.ALL;

        private static string TAG = "KeychainViewModel";
        private static string PERSONAS_MAP_SIZE = "personasMap.size = ";
        public static string PENDING_PERSONA_MAP_SIZET = "pendingPersonaMap.size = ";
        public static string PLATFORM_USERS_SIZE = "platformUsers.size = ";
        public static string USER_MAP_SIZE = "userMap.size ";
        public static string USERS_COUNT = "users.count = ";
        public static string TEMP_USERS_COUNT = "tempUsers.count = ";

        private Logger log = LogManager.GetCurrentClassLogger();

        public ObservableCollection<UserModel> Users { get; set; }

        public ObservableCollection<ContactModel> ContactModels { get; set; }

        public ObservableCollection<MessageModel> Messages { get; set; }

        public RelayCommand SendCommand { get; set; }

        public Persona? ActivePersona
        {
            get => activePersona;
            set
            {
                activePersona = value;
                ActivePersonaInitials = $"{ActivePersona?.getName().Trim()[0]}{ActivePersona?.getSubName().Trim()[0]}";
                ActivePersonaName = $"{ActivePersona?.getName().Trim()} {ActivePersona?.getSubName().Trim()}";
                ActivePersonaUri = GetMyUri();

                OnPropertyChanged();
            }
        }

        private string activePersonaName;

        public string ActivePersonaName
        {
            get
            {
                return activePersonaName;
            }

            set
            {
                activePersonaName = value;
                OnPropertyChanged();
            }
        }

        public string? ActivePersonaUri
        {
            get
            {
                return GetMyUri();
            }

            set
            {
                OnPropertyChanged();
            }
        }

        private string activePersonaInitials;

        public string ActivePersonaInitials
        {
            get
            {
                return activePersonaInitials;
            }

            set
            {
                activePersonaInitials = value;
                OnPropertyChanged();
            }
        }

        public bool IsLoggedIn { get; set; } = false;

        public UserModel selectedUser;

        public UserModel? SelectedUser
        {
            get { return selectedUser; }
            set
            {
                selectedUser = value;
                IsUserSelected = value != null;
                SignInVisible = (!IsLoggedIn && (value != null)) 
                    ? Visibility.Visible : Visibility.Hidden;
                SignInEnabled = (value != null && value.Status == PersonaStatus.Confirmed)
                    ? true : false;

                UpdateMessageBoxEnabled();
                OnPropertyChanged();
            }
        }

        private bool signInEnabled;

        public bool SignInEnabled
        {
            get => signInEnabled;
            set
            {
                signInEnabled = value;
                OnPropertyChanged();
            }
        }

        private Visibility signInVisible;

        public Visibility SignInVisible
        {
            get => signInVisible;
            set
            {
                signInVisible = value;
                OnPropertyChanged();
            }
        }

        private Visibility signOutVisible;

        public Visibility SignOutVisible
        {
            get => signOutVisible;
            set
            {
                signOutVisible = value;
                OnPropertyChanged();
            }
        }

        private bool isMessageBoxEnabled;

        public bool IsMessageBoxEnabled
        {
            get => isMessageBoxEnabled;
            set
            {
                isMessageBoxEnabled = value;
                OnPropertyChanged();
            }
        }

        private Persona? GetPersonaFromDict(string key)
        {
            return Personas.ContainsKey(key) ? Personas[key] : null;
        }

        private ContactModel selectedContact;

        public ContactModel SelectedContact
        {
            get { return selectedContact; }
            set
            {
                selectedContact = value;

                if (selectedContact != null)
                {
                    if (selectedContact.Uri == ALL)
                    {
                        selectedContact.ChatMessages = GetChatMessages(GetMyUri(),
                                                                       selectedContact.Name,
                                                                       "",
                                                                       selectedContact.Uri);
                    }
                    else
                    {
                        var contact = Contacts.GetValueOrDefault(selectedContact.Uri);

                        if (contact != null)
                        {
                            string? contactUri = StringUtils.GetUriString(contact.getUri());

                            if (contactUri != null)
                            {
                                selectedContact.ChatMessages = GetChatMessages(GetMyUri(),
                                                                               contact.getName(),
                                                                               contact.getSubName(),
                                                                               contactUri);
                            }
                        }
                    }

                    Messages = selectedContact.ChatMessages;
                    ScrollToEnd(Messages);
                }

                UpdateMessageBoxEnabled();
                OnPropertyChanged();
            }
        }

        private Visibility contactsTabVisible = Visibility.Hidden;

        public Visibility ContactsTabVisible
        {
            get
            {
                return contactsTabVisible;
            }

            set
            {
                contactsTabVisible = value;
                OnPropertyChanged();
            }
        }

        private bool isUserSelected = false;

        public bool IsUserSelected
        {
            get
            {
                return isUserSelected;
            }

            set
            {
                isUserSelected = value;
                OnPropertyChanged();
            }
        }

        private string message;

        public string Message
        {
            get { return message; }
            set
            {
                message = value;
                OnPropertyChanged();
            }
        }

        private IDialogService personaDialogService;

        private IDialogService qrCodeDialogService;

        public ICommand CreatePersonaCommand { get; private set; }

        public ICommand EditPersonaCommand { get; private set; }

        public ICommand DeletePersonaCommand { get; private set; }

        public ICommand SignInCommand { get; private set; }

        public ICommand SignOutCommand { get; private set; }

        public ICommand PairUsingDirectoryCommand { get; private set; }

        public ICommand PairUsingQRCodeCommand { get; private set; }

        private KeychainService keychainService = KeychainService.GetInstance();

        private MqttService mqttService;

        private DirectoryService directoryService = new DirectoryService();

        private Dictionary<string, Persona> Personas = new Dictionary<string, Persona>();

        private Dictionary<string, Contact> Contacts = new Dictionary<string, Contact>();

        private Dictionary<string, PendingPersona> PendingPersonas = new Dictionary<string, PendingPersona>();

        private Dictionary<string, User> UserDict = new Dictionary<string, User>();
        private Persona? activePersona = null;

        public ConnectionStatus MqttConnectionStatus { get; private set; }

        private readonly string mqttChannelPairing = ConfigurationManager.AppSettings.Get("MqttChannelPairing");

        private readonly string mqttChannelChats = ConfigurationManager.AppSettings.Get("MqttChannelChats");

        private ListView ChatListView => ((MainWindow)Application.Current?.MainWindow).ChatListView;

        private TabControl Tabs => ((MainWindow)Application.Current?.MainWindow).Tabs;

        private TabItem PersonasTab => ((MainWindow)Application.Current?.MainWindow).PersonasTab;

        private TabItem ContactsTab => ((MainWindow)Application.Current?.MainWindow).ContactsTab;

        private Mutex refreshMutex = new Mutex();

        public KeychainViewModel()
        {
            Users = new ObservableCollection<UserModel>();
            ContactModels = new ObservableCollection<ContactModel>();
            Messages = new ObservableCollection<MessageModel>();
            SignOutVisible = Visibility.Hidden;
            SignInVisible = Visibility.Visible;

            CreatePersonaCommand = new RelayCommand(o => CreatePersona());
            EditPersonaCommand = new RelayCommand(o => EditPersona());
            DeletePersonaCommand = new RelayCommand(o => DeletePersona());
            SignInCommand = new RelayCommand(o => SignIn());
            SignOutCommand = new RelayCommand(o => SignOut());
            PairUsingDirectoryCommand = new RelayCommand(o => PairUsingDirectoryAsync());
            PairUsingQRCodeCommand = new RelayCommand(o => PairUsingQRCode());
            SendCommand = new RelayCommand(o => SendMessage());

            personaDialogService = new PersonaDialogService();
            qrCodeDialogService = new QRCodeDialogService();

            keychainService.AddListener(this);
            keychainService.StartMonitor();

            mqttService = MqttService.GetInstance(this);
            mqttService.AddStatusListener(this);
            mqttService.InitializeMqtt();

            _ = Task.Run(() =>
            {
                OnRefresh(this, EventArgs.Empty);
            });
        }

        private void PairUsingQRCode()
        {
            if (ActivePersona == null)
            {
                ShowError(Constants.NO_ACTIVE_PERSONA);
                return;
            }

            var qrCodeData = new AccountQRCodeData()
            {
                firstName = ActivePersona.getName(),
                lastName = ActivePersona.getSubName(),
                id = GetMyUri()
            };

            var dialog = new QRCodeViewModel(qrCodeData);
            _ = qrCodeDialogService.OpenDialog(dialog);
        }

        private async Task PairUsingDirectoryAsync()
        {
            Debug.WriteLine(Constants.PAIRING_USING_TRUSTED_DIRECTORY);

            if (ActivePersona == null)
            {
                ShowError(Constants.NO_ACTIVE_PERSONA);
                return;
            }

            var uriList = directoryService.DownloadUris();

            if (uriList == null || uriList.Count == 0)
            {
                ShowMessage(Constants.TRUSTED_DIRECTORY_IS_EMPTY);
                return;
            }

            await Task.Run(() =>
            {
                try
                {
                    Debug.WriteLine($"{Constants.SENDING_PAIR_REQUEST_TO} {uriList.Count} recipients");

                    foreach (var uri in uriList)
                    {
                        // Don't pair with self
                        if (Personas.ContainsKey(uri))
                        {
                            continue;
                        }

                        PairingMessage message = ChannelMessage.MakePairRequest(ActivePersona, uri);

                        if (message == null)
                        {
                            ShowError(Constants.ERROR_MAKING_PAIRING_REQUEST);
                            continue;
                        }

                        var request = JsonConvert.SerializeObject(message);

                        if (request == null)
                        {
                            ShowError(Constants.ERROR_SERIALIZING_PAIRING_REQUEST);
                            return;
                        }

                        Debug.WriteLine($"{Constants.SENDING_PAIR_REQUEST_TO} {uri}");

                        SendToMqtt($"{mqttChannelPairing}{uri}", request);
                    }
                }
                catch (Exception e)
                {
                    ShowError(e);
                }
            });
        }

        private string? GetMyUri()
        {
            if (ActivePersona == null)
            {
                return null;
            }

            return StringUtils.GetUriString(ActivePersona.getUri());
        }

        private void DeletePersona()
        {
            try
            {
                if (SelectedUser == null)
                {
                    return;
                }

                if (ActivePersona != null &&
                    SelectedUser.Uri == GetMyUri())
                {
                    ShowError(Constants.CANNOT_DELETE_ACTIVE_PERSONA);
                    return;
                }

                var persona = GetPersonaFromDict(SelectedUser.Uri);

                if (persona == null)
                {
                    ShowError(Constants.ERROR_FINDING_PERSONA + SelectedUser.Name);
                }

                keychainService.DeletePersona(persona);
                ShowMessage(Constants.SUCCESSFULLY_DELETED_PERSONA + SelectedUser.Name);

                RemoveUser(SelectedUser);
            }
            catch (Exception ex)
            {
                ShowError(ex.Message);
            }
        }

        private void RemoveUser(UserModel userModel)
        {
            var user = GetUserFromDict(userModel.Uri);

            if (user == null)
            {
                user = GetUserFromDict(userModel.Name);
            }

            if (user != null)
            {

                IChatRepository chatRepository = SQLiteDBService.GetDBService();
                chatRepository.DeletePlatformUser(user);
            }

            RemoveUser(Personas, userModel);
            RemoveUser(PendingPersonas, userModel);
            RemoveUser(UserDict, userModel);

            Users.Remove(userModel);
        }

        private void RemoveUser<T>(Dictionary<string, T> dictionary, UserModel user)
        {
            if (dictionary.ContainsKey(user.Uri))
            {
                dictionary.Remove(user.Uri);
            }
            else if (dictionary.ContainsKey(user.Name))
            {
                dictionary.Remove(user.Name);
            }
        }

        private void ShowMessage(string msg)
        {
            Application.Current?.Dispatcher.Invoke(() =>
            {
                Debug.WriteLine(msg);
                MessageBox.Show(msg);
            });
        }

        private void ShowError(string msg)
        {
            Application.Current?.Dispatcher.Invoke(() =>
            {
                Debug.WriteLine(msg);
                MessageBox.Show(msg, Constants.ERROR);
            });
        }

        private void EditPersona()
        {
            if (SelectedUser == null)
            {
                return;
            }

            if (SelectedUser.Status != PersonaStatus.Confirmed)
            {
                ShowError(Constants.ONLY_CONFIRMED_PERSONAS_CAN_BE_EDITED);
                return;
            }

            var user = GetUserFromDict(SelectedUser.Uri);

            if (user == null)
            {
                ShowError(Constants.ERROR_FINDING_PERSONA);
                return;
            }

            var dialog = new PersonaViewModel()
            {
                FirstName = user.FirstName,
                LastName = user.LastName,
            };

            CreatePersonaDialogResult result = personaDialogService.OpenDialog(dialog);

            if (result != null)
            {
                UpdatePersona(user, SelectedUser, result);
            }
        }

        private void UpdatePersona(User user, UserModel selectedUser, CreatePersonaDialogResult result)
        {
            try
            {
                if (user.FirstName == result.FirstName && user.LastName == result.LastName)
                {
                    return;
                }

                if (!Personas.ContainsKey(user.Uri))
                {
                    ShowError(Constants.ERROR_FINDING_PERSONA + user.GetName());
                    return;
                }

                var persona = Personas[user.Uri];

                keychainService.ModifyPersona(persona,
                                              result.FirstName,
                                              result.LastName);

                Personas.Remove(user.Uri);

                IChatRepository chatRepository = SQLiteDBService.GetDBService();
                chatRepository.RenamePlatformUser(user, result.FirstName, result.LastName);

                RemoveUser(selectedUser);
                OnRefresh(this, EventArgs.Empty);
            }
            catch (Exception e)
            {
                ShowError(e);
            }
        }

        private void ShowError(Exception e)
        {
            Debug.WriteLine(e);
            ShowError(e.Message);
        }

        private void CreatePersona()
        {
            var dialog = new PersonaViewModel();
            CreatePersonaDialogResult result = personaDialogService.OpenDialog(dialog);

            if (result != null)
            {
                SavePersona(result);
            }
        }

        private async void SignIn()
        {
            try
            {
                if (SelectedUser?.Status == PersonaStatus.Confirmed)
                {
                    var uri = SelectedUser.Uri;
                    var persona = GetPersonaFromDict(uri);

                    if (persona != null)
                    {
                        keychainService.SetActivePersona(persona);
                        ActivePersona = keychainService.GetActivePersona();

                        if (ActivePersona == null)
                        {
                            throw new Exception(Constants.NO_ACTIVE_PERSONA);
                        }

                        IsLoggedIn = true;

                        GetContactsAndAddToListView();

                        EnableSignIn(false);

                        if (MqttConnectionStatus != ConnectionStatus.CONNECTED)
                        {
                            await mqttService.ConnectAsync();

                            if (MqttConnectionStatus != ConnectionStatus.CONNECTED)
                            {
                                ShowError($"{Constants.SOMETHING_WENT_WRONG} - {Constants.MQTT_NOT_CONNECTED}");
                                return;
                            }
                        }

                        Tabs.SelectedItem = ContactsTab;

                        await Task.Run(async () =>
                        {
                            await Task.Delay(500);
                            await Application.Current?.Dispatcher.BeginInvoke(new Action(() => SubscribeToMqttTopic(ActivePersona)));
                        });

                        // Upload my receiverId to the trusted directory
                        _ = Task.Run(() =>
                        {
                            directoryService.UploadUri(uri);
                        });

                        return;
                    }
                }

                EnableSignIn(true);
            }
            catch (Exception e)
            {
                SignOut();
                ShowError(e);
            }
            finally
            {
                UpdateMessageBoxEnabled();
            }
        }

        private void SignOut()
        {
            try
            {
                foreach (var topic in GetMyTopics(GetMyUri()))
                {
                    mqttService.Unsubscribe(topic);
                }

                ActivePersona = null;
                SelectedUser = null;
                IsLoggedIn = false;
                Tabs.SelectedItem = PersonasTab;

                EnableSignIn(true);

                foreach (var contactModel in ContactModels)
                {
                    contactModel.ChatMessages.Clear();
                }

                ContactModels.Clear();
            }
            finally
            {
                UpdateMessageBoxEnabled();
            }
        }

        private void UpdateMessageBoxEnabled()
        {
            IsMessageBoxEnabled = ActivePersona != null && SelectedContact != null;
        }

        private void EnableSignIn(bool enable)
        {
            if (enable)
            {
                SignInVisible = Visibility.Visible;
                SignOutVisible = Visibility.Hidden;
            }
            else
            {
                SignInVisible = Visibility.Hidden;
                SignOutVisible = Visibility.Visible;
            }

            ContactsTabVisible = SignOutVisible;
        }

        private void SavePersona(CreatePersonaDialogResult dialogResult)
        {
            if (string.IsNullOrWhiteSpace(dialogResult.FirstName) || string.IsNullOrWhiteSpace(dialogResult.LastName))
            {
                MessageBox.Show(Constants.FIRST_AND_LAST_NAMES_REQUIRED,
                                Constants.ERROR,
                                MessageBoxButton.OK,
                                MessageBoxImage.Error);

                return;
            }

            string firstName = dialogResult.FirstName;
            string lastName = dialogResult.LastName;

            SavePlatformUserIsNotExists(firstName, lastName, PersonaStatus.Created, "");

            _ = Task.Run(() =>
            {
                keychainService.CreatePersona(firstName, lastName, SecurityLevel.Medium);
            });

            OnRefresh(this, EventArgs.Empty);
        }

        private string SavePlatformUserIsNotExists(
            string firstName,
            string lastName,
            PersonaStatus status,
            string uri,
            bool warnIfExists = true)
        {
            IChatRepository chatRepository = SQLiteDBService.GetDBService();
            var user = chatRepository.GetPlatformUser(firstName, lastName);

            if (user == null)
            {
                var recordId = chatRepository.SaveUserProfile(firstName,
                                                              lastName,
                                                              status,
                                                              uri);

                if (warnIfExists && string.IsNullOrWhiteSpace(recordId))
                {
                    // May be recoverable, So continue even after this error
                    Debug.WriteLine(Constants.ERROR_ADDING_USER_TO_CHAT_DB);

                    ShowError($"{Constants.ERROR_ADDING_USER_TO_CHAT_DB}{firstName} {lastName}");
                }

                return recordId;
            }
            else if (warnIfExists)
            {
                var msg = $"{Constants.USER_ALREADY_EXISTS} {firstName} {lastName}";
                Debug.WriteLine(msg);

                MessageBox.Show(msg,
                                Constants.WARNING,
                                MessageBoxButton.OK,
                                MessageBoxImage.Warning);
            }

            return user.Id;
        }

        private void SendMessage()
        {
            try
            {
                if (string.IsNullOrWhiteSpace(Message))
                {
                    return;
                }

                string receiverId = SelectedContact.Uri;

                if (receiverId != ALL && !Contacts.ContainsKey(receiverId))
                {
                    ShowError($"{Constants.ERROR_FINDING_CONTACT} {SelectedContact.Name}");
                    return;
                }

                IList<Contact> contacts = new List<Contact>();

                if (receiverId == ALL)
                {
                    contacts = (IList<Contact>)keychainService.GetContacts();
                }
                else
                {
                    contacts.Add(Contacts[receiverId]);
                }

                // TODO: Encrypt sign and send to MQTT
                var encryptedMessage = keychainService.SignThenEncrypt(contacts,
                                                                       Message);

                if (encryptedMessage == null)
                {
                    ShowError(Constants.ERROR_ENCRYPTING_MESSAGE);
                    return;
                }

                string? senderId = GetMyUri();

                if (senderId == null)
                {
                    ShowError(Constants.ERROR_GETTING_PERSONA_URI);
                    return;
                }

                var chat = GetOrCreateChat(senderId, receiverId);
                chat.LastMsg = encryptedMessage;

                IChatRepository chatRepository = SQLiteDBService.GetDBService();
                var recordId = chatRepository.SaveChat(chat);

                if (string.IsNullOrWhiteSpace(recordId))
                {
                    ShowError(Constants.ERROR_SAVING_CHAT_TO_DB);
                    return;
                }

                recordId = chatRepository.SaveMessage(null,
                                           senderId,
                                           encryptedMessage,
                                           ChatDirection.Send,
                                           chat,
                                           null);

                if (recordId == null)
                {
                    ShowError(Constants.ERROR_SAVING_MESSAGE);
                    return;
                }

                var chatMessage = chatRepository.GetMessage(recordId);

                if (chatMessage == null)
                {
                    ShowError(Constants.ERROR_GETTING_SAVED_CHAT_MESSAGE);
                    return;
                }

                chatMessage.Msg = StringUtils.ToBase64Encoding(encryptedMessage);
                var json = JsonConvert.SerializeObject(chatMessage);

                mqttService.Send($"{mqttChannelChats}{receiverId}", json);

                DisplayMessage(senderId, receiverId, Message);

                Message = string.Empty;
            }
            catch (Exception e)
            {
                ShowError(e);
            }
        }

        private Chat GetOrCreateChat(string senderId, string receiverId)
        {
            if (string.IsNullOrWhiteSpace(senderId))
            {
                throw new ArgumentException($"'{nameof(senderId)}' cannot be null or whitespace.", nameof(senderId));
            }

            if (string.IsNullOrWhiteSpace(receiverId))
            {
                throw new ArgumentException($"'{nameof(receiverId)}' cannot be null or whitespace.", nameof(receiverId));
            }

            IChatRepository chatRepository = SQLiteDBService.GetDBService();
            var chat = chatRepository.GetChat(senderId, receiverId);

            if (chat == null)
            {
                Debug.WriteLine($"{Constants.NO_EXISTING_CHAT_FOR} {senderId}/{receiverId}");
                Debug.WriteLine($"{Constants.CREATING_NEW_CHAT_FOR} {senderId}/{receiverId}");

                chat = new()
                {
                    ParticipantIds = $"{senderId},{receiverId}",
                    Timestamp = DateTime.Now,
                };
            }

            return chat;
        }

        public void OnRefresh(object? source, EventArgs? args)
        {
            bool aquired = false;

            try
            {
                aquired = refreshMutex.WaitOne(0);

                if (aquired)
                {
                    GetPersonas();
                    GetUsers();
                }
            }
            catch (Exception ex)
            {
                Debug.WriteLine($"{Constants.SOMETHING_WENT_WRONG} {ex.Message}");
            }
            finally
            {
                if (aquired)
                {
                    refreshMutex.ReleaseMutex();
                }
            }
        }

        private void GetUsers()
        {
            try
            {
                IList<User> platformUsers = GetChatPersonas();

                UpdatePersonasCollection(platformUsers);
            }
            catch (Exception e)
            {
                Debug.WriteLine(Constants.SOMETHING_WENT_WRONG, e);
            }
        }

        private void UpdatePersonasCollection(IList<User> platformUsers)
        {
            Debug.WriteLine(PERSONAS_MAP_SIZE + Personas.Count);
            Debug.WriteLine(PENDING_PERSONA_MAP_SIZET + PendingPersonas.Count);
            Debug.WriteLine(USER_MAP_SIZE + UserDict.Count);
            Debug.WriteLine("");

            if (platformUsers.Count > 0)
            {
                IList<User> tempUsers = new List<User>();

                PreservePendingPersonas(tempUsers, platformUsers);
                UpdateStatusOfExistingUsers(tempUsers, platformUsers);

                Debug.WriteLine(TEMP_USERS_COUNT + tempUsers.Count);
                Debug.WriteLine(PLATFORM_USERS_SIZE + Users.Count);
                Debug.WriteLine("");
            }
        }

        private void UpdateStatusOfExistingUsers(IList<User> tempUsers, IList<User> platformUsers)
        {
            foreach (User incoming in platformUsers)
            {
                User? user = GetUserFromDict(incoming.GetKey());

                if (user == null)
                {
                    UpdateUsersDict(tempUsers, incoming);
                    continue;
                }

                // If the status of the existing userModel is different from the incomming
                if (user.Status != incoming.Status)
                {
                    UpdateUsersDict(tempUsers, incoming);
                }
                else if (tempUsers.Count > 0)
                {
                    // If at least one status updated, we need to copy the rest of the users so all
                    // users will apprear in the UI. Otherwise, only the updated ones will appear
                    tempUsers.Add(user);
                }
            }
        }

        private User? GetUserFromDict(string key)
        {
            return UserDict.ContainsKey(key) ? UserDict[key] : null;
        }

        private void UpdateUsersDict(IList<User> tempUsers, User incoming)
        {
            tempUsers.Add(incoming);
            AddUser(incoming);
        }

        private void PreservePendingPersonas(IList<User> tempUsers, IList<User> platformUsers)
        {
            foreach (User user in platformUsers)
            {
                try
                {
                    if (user.Status == (int)PersonaStatus.Confirmed)
                    {
                        if (PendingPersonas.ContainsKey(user.GetName()))
                        {
                            PendingPersonas.Remove(user.GetName());
                        }

                        continue;
                    }

                    var persona = FindPersona(user.Uri);

                    if (persona == null)
                    {
                        continue;
                    }

                    // Add image later, maybe
                    User pUser = CreatePendingUser(user.Id, persona.getName(), persona.getSubName());
                    pUser.Photo = user.Photo;

                    tempUsers.Insert(0, pUser);

                    // At first the pending userModel has no receiverId, so the name is used as the key
                    // After the receiverId exists we use the receiverId as the key
                    if (UserDict.ContainsKey(pUser.GetName()))
                    {
                        UserDict.Remove(pUser.GetName());    // If it was previously pending, remove it
                    }

                    AddUser(pUser);

                    SaveToDatabase(persona);
                }
                catch (Exception e)
                {
                    Debug.WriteLine("Error processing pending persona", e);
                }
            }
        }

        private void SaveToDatabase(Persona persona)
        {
            try
            {
                string? uri = persona.getUri() != null
                    ? StringUtils.GetUriString(persona.getUri())
                    : null;
                string firstName = persona.getName();
                string lastName = persona.getSubName();
                IChatRepository chatRepository = SQLiteDBService.GetDBService();

                if (chatRepository.UpdateUserProfile(firstName,
                                                     lastName,
                                                     persona.getStatus(),
                                                     uri))
                {

                    User? user = chatRepository.GetPlatformUser(firstName, lastName);

                    if (user != null)
                    {
                        Debug.WriteLine(Constants.PERSONA_SUCCESSFULLY_UPDATED_IN_CHATS_DATABASE_FOR + firstName + " " + lastName);
                        Debug.WriteLine(Constants.RECORD_ID + user.Id + " - " + firstName + " " + lastName);

                        PendingPersona? pUser = GetPendingPersona(user.GetName());

                        if (pUser == null && persona.getStatus() != PersonaStatus.Confirmed)
                        {
                            CreatePendingPersona(uri, persona.getName(), persona.getSubName());
                        }

                        return;
                    }
                }

                Debug.WriteLine(Constants.ERROR_UPDATING_PERSONA_PROFILE_TO_CHATS_DATABASE_FOR + firstName + " " + lastName);
            }
            catch (Exception ex)
            {
                Debug.WriteLine(Constants.FAILED_TO_SAVE_PERSONA_TO_CHATS_DATABASE, ex);
            }
        }

        private IList<User> GetChatPersonas()
        {
            AddChatUsers(Personas);
            AddAllChatUserIfMissing();

            IList<User> userList = new List<User>();

            foreach (var keyValue in Personas)
            {
                var uri = StringUtils.GetUriString(keyValue.Value.getUri());

                User? user = null;
                IChatRepository chatRepository = SQLiteDBService.GetDBService();

                if (uri != null)
                {
                    user = chatRepository.GetPlatformUserByUri(uri);
                }
                else
                {
                    var persona = keyValue.Value;
                    user = chatRepository.GetPlatformUser(persona.getName(), persona.getSubName());
                }

                if (user != null)
                {
                    userList.Add(user);
                }
            }

            return userList;
        }

        private void AddAllChatUserIfMissing()
        {
            if (ContactModels.Count >= 2 && !UserDict.ContainsKey(ALL))
            {
                IChatRepository chatRepository = SQLiteDBService.GetDBService();
                var allUser = chatRepository.GetPlatformUserByUri(ALL);

                if (allUser == null)
                {
                    var recordId = SavePlatformUserIsNotExists(ALL, "", PersonaStatus.Confirmed, ALL);

                    if (recordId != null)
                    {
                        allUser = chatRepository.GetPlatformUser(recordId);
                    }
                }

                if (allUser == null)
                {
                    Debug.WriteLine(Constants.ERROR_CREATING_RECORD_FOR_ALL_USER_CHAT);
                    return;
                }

                UserDict[ALL] = allUser;
            }
        }

        private void AddChatUsers(Dictionary<string, Persona> personaDict)
        {
            try
            {
                foreach (var keyValue in personaDict)
                {
                    var persona = keyValue.Value;

                    IChatRepository chatRepository = SQLiteDBService.GetDBService();
                    var user = chatRepository.GetPlatformUser(
                        persona.getName(),
                        persona.getSubName());

                    string? uri = null;

                    try
                    {
                        uri = StringUtils.GetUriString(persona.getUri());
                    }
                    catch (Exception e)
                    {
                        // Doesnot have a receiverId yet
                    }

                    if (uri == null)
                    {
                        uri = string.Empty;
                    }

                    if (user == null)
                    {
                        _ = SavePlatformUserIsNotExists(persona.getName(),
                                                        persona.getSubName(),
                                                        persona.getStatus(),
                                                        uri,
                                                        false);
                    }
                    else if (user.Status != (int)persona.getStatus() ||
                            (!string.IsNullOrWhiteSpace(uri) && !user.Uri.Equals(uri)))
                    {

                        chatRepository.UpdateUserProfile(user.FirstName,
                                                         user.LastName,
                                                         persona.getStatus(),
                                                         uri);
                    }

                    if (user != null)
                    {
                        AddUser(user);
                    }
                }
            }
            catch (Exception e)
            {
                Debug.WriteLine(Constants.ERROR_ADDING_USER_TO_CHAT_DB, e);
            }
        }

        private void AddUser(User user)
        {
            // Case for pending persona
            if (UserDict.ContainsKey(user.GetName()))
            {
                var oldUser = UserDict[user.GetName()];

                if (oldUser.Status != user.Status)
                {
                    UserDict.Remove(user.GetName());    // If it was previously pending, remove it
                }
            }
            // Case were userModel was already added
            else if (UserDict.ContainsKey(user.GetKey()))
            {
                var existing = UserDict[user.GetKey()];

                if (existing.Status != user.Status)
                {
                    UserDict.Remove(user.GetKey());
                }
            }

            // Not yet added
            AddOrUpdateUser(user);
        }

        private void AddOrUpdateUser(User user)
        {
            UserDict[user.GetKey()] = user;

            Application.Current?.Dispatcher.BeginInvoke(() =>
            {
                UserModel? userModel = null;

                foreach (UserModel model in Users)
                {
                    if (model.Name == user.GetName())
                    {
                        userModel = model;
                        break;
                    }
                }

                if (userModel != null && userModel.Status != (PersonaStatus)user.Status)
                {
                    var index = Users.IndexOf(userModel);
                    Users.Remove(userModel);
                    Users.Insert(index, new UserModel(user));
                }
                else if (userModel == null)
                {
                    Users.Add(new UserModel(user));
                }
            });
        }

        private IList<Facade> GetContactsAndAddToListView()
        {
            log.Debug(TAG, "Getting contacts for active persona.");

            if (ActivePersona == null)
            {
                Debug.WriteLine(Constants.NO_ACTIVE_PERSONA);
                return Array.Empty<Facade>();
            }

            ContactModel contactModel = new()
            {
                Name = ALL,
                FirstName = ALL,
                ChatMessages = GetChatMessages(GetMyUri(), ALL, "", ALL),
                Uri = ALL,
            };

            AddContact(contactModel);

            IList<Facade> contactList = keychainService.GetContacts();

            foreach (Contact contact in contactList)
            {
                try
                {
                    // Make sure it has a valid URI.
                    StringUtils.GetUriString(contact.getUri());
                }
                catch (Exception e)
                {
                    Debug.WriteLine("Contact has no URI, deleting it.", e);
                    keychainService.DeleteContact(contact);
                    continue;
                }

                try
                {
                    AddContact(contact);
                }
                catch (Exception ex)
                {
                    Debug.WriteLine(Constants.ERROR_GETTING_CONTACTS, ex);
                    return Array.Empty<Facade>();
                }
            }

            return contactList;
        }

        private void AddContact(Contact contact)
        {
            var contactUri = StringUtils.GetUriString(contact.getUri());

            if (contactUri != null)
            {
                // Incase a persona was accidentally added to contacts
                if (Personas.ContainsKey(contactUri))
                {
                    return;
                }

                Contacts[contactUri] = contact;

                var name = $"{contact.getName()} {contact.getSubName()}";

                ContactModel contactModel = new()
                {
                    Name = name,
                    FirstName = contact.getName(),
                    LastName = contact.getSubName(),
                    ChatMessages = GetChatMessages(GetMyUri(),
                                                   contact.getName(),
                                                   contact.getSubName(),
                                                   contactUri),
                    Uri = contactUri,
                };

                AddContact(contactModel);
            }
        }

        private void AddContact(ContactModel contactModel)
        {
            Application.Current?.Dispatcher.Invoke(new Action(() =>
            {
                if (FindContactModel(contactModel.Name) != null)
                {
                    return;
                }

                ContactModels.Add(contactModel);
                OnPropertyChanged();
            }));
        }

        private ContactModel? FindContactModel(string name)
        {
            ContactModel? contactModel = null;

            foreach (var contact in ContactModels)
            {
                if (contact.Name.Equals(name))
                {
                    contactModel = contact;
                    break;
                }
            }

            return contactModel;
        }

        private IList<Facade> GetPersonas()
        {
            try
            {
                IList<Facade> personasList = keychainService.GetPersonas();

                foreach (Facade facade in personasList)
                {
                    var persona = (Persona)facade;

                    var name = persona.getName() + " " + persona.getSubName();
                    var status = persona.getStatus();
                    string? uri = StringUtils.GetUriString(persona.getUri());

                    if (status == PersonaStatus.Confirmed)
                    {
                        if (PendingPersonas.ContainsKey(name))
                        {
                            PendingPersona pendingPersona = PendingPersonas[name];

                            // Pending persona is initially created with a null persona because
                            // persona creation takes a few seconds
                            pendingPersona.persona ??= persona;

                            SaveConfirmedPersonaToDB(persona);
                        }
                    }
                    else
                    {
                        var pendingPersona = PendingPersonas.ContainsKey(name)
                            ? PendingPersonas[name]
                            : CreatePendingPersona(uri,
                                                   persona.getName(),
                                                   persona.getSubName());

                        PendingPersonas[name] = pendingPersona;
                    }

                    if (uri != null && uri.Length > 0)
                    {
                        if (Personas.ContainsKey(name))
                        {
                            Personas.Remove(name);
                        }

                        Personas[uri] = persona;
                    }
                    else
                    {
                        Personas[name] = persona;
                    }
                }

                return personasList;
            }
            catch (Exception e)
            {
                Debug.WriteLine(Constants.ERROR_GETTING_PERSONAS, e);
                return Array.Empty<Persona>();
            }
        }

        private PendingPersona CreatePendingPersona(string? uri, string firstName, string lastName)
        {
            PendingPersona pendingPersona;
            string name = $"{firstName} {lastName}";
            User? user = UserDict.ContainsKey(name) ? UserDict[name] : null;

            if (user == null)
            {
                user = CreatePendingUser(uri,
                                         firstName,
                                         lastName);
                UserDict[name] = user;
            }

            pendingPersona = new PendingPersona(user.GetKey(), null, null);

            return pendingPersona;
        }

        private User CreatePendingUser(string? uri, string firstName, string lastName)
        {

            User user = new User()
            {
                FirstName = firstName,
                LastName = lastName,
                Status = (int)PersonaStatus.Created,
                Uri = uri,
            };

            UserDict[user.GetKey()] = user;

            return user;
        }

        private void SaveConfirmedPersonaToDB(Persona persona)
        {
            try
            {
                var uri = persona.getUri() != null
                    ? StringUtils.GetUriString(persona.getUri())
                    : null;
                var firstName = persona.getName();
                var lastName = persona.getSubName();
                var status = persona.getStatus();

                Debug.WriteLine($"{Constants.ERROR_SAVING_USER_PROFILE}: {firstName} {lastName}");

                IChatRepository chatRepository = SQLiteDBService.GetDBService();

                if (chatRepository.UpdateUserProfile(firstName,
                                                     lastName,
                                                     status,
                                                     uri))
                {

                    User? user = chatRepository.GetPlatformUser(firstName, lastName);

                    if (user != null)
                    {
                        Debug.WriteLine(Constants.PERSONA_SUCCESSFULLY_UPDATED_IN_CHATS_DATABASE_FOR + firstName + " " + lastName);
                        Debug.WriteLine(Constants.RECORD_ID + user.Id + " - " + firstName + " " + lastName);
                        PendingPersona? pendingPersona = GetPendingPersona(user.GetName());

                        if (pendingPersona == null && persona.getStatus() != PersonaStatus.Confirmed)
                        {
                            pendingPersona = CreatePendingPersona(uri, persona.getName(), persona.getSubName());
                            PendingPersonas[user.GetName()] = pendingPersona;
                        }

                        return;
                    }
                }

                Debug.WriteLine(Constants.ERROR_UPDATING_PERSONA_PROFILE_TO_CHATS_DATABASE_FOR + firstName + " " + lastName);
            }
            catch (Exception ex)
            {
                Debug.WriteLine(Constants.FAILED_TO_SAVE_PERSONA_TO_CHATS_DATABASE, ex);
            }
        }

        private PendingPersona? GetPendingPersona(string name)
        {
            return PendingPersonas.ContainsKey(name) ? PendingPersonas[name] : null;
        }

        public Persona? FindPersona(string uri)
        {
            return Personas.ContainsKey(uri) ? Personas[uri] : null;
        }

        private ObservableCollection<MessageModel> GetChatMessages(string myUri, 
            string contactFirstName, 
            string contactLastName, 
            string contactUri)
        {
            var chatMessages = new ObservableCollection<MessageModel>();

            try
            {
                IChatRepository chatRepository = SQLiteDBService.GetDBService();
                var chat = chatRepository.GetChat(myUri, contactUri);

                if (chat != null)
                {
                    var messages = chatRepository.GetAllMessages(chat);

                    foreach (var chatMessage in messages)
                    {
                        var decriptedMessage = keychainService.DecryptThenVerify(chatMessage.Msg);

                        bool isFromMe = myUri.Equals(chatMessage.SenderId);

                        string firstName;
                        string lastName;

                        if (!isFromMe && contactFirstName == Constants.ALL)
                        {
                            var contact = keychainService.FindContact(chatMessage.SenderId);

                            firstName = contact != null
                                ? contact.getName()
                                : contactFirstName;

                            lastName = contact != null
                                ? contact.getSubName()
                                : contactLastName;
                        }
                        else
                        {
                            firstName = isFromMe ? ActivePersona.getName() : contactFirstName;
                            lastName = isFromMe ? ActivePersona.getSubName() : contactLastName;
                        }

                        chatMessages.Add(new MessageModel()
                        {
                            FirstName = firstName,
                            LastName = lastName,
                            Message = decriptedMessage,
                            Time = DateUtils.ParseUnixTimestamp(chatMessage.Timestamp),
                            IsFromMe = isFromMe,
                        });
                    }

                    ScrollToEnd(chatMessages);
                }
            }
            catch (Exception e)
            {
                Debug.WriteLine(Constants.ERROR_GETTING_CHATS, e);
            }

            return chatMessages;
        }

        public void OnReceiveMqttMessage(object source, EventArgs message)
        {
            try
            {
                var args = (MessageRecievedEventArgs)message;

                if (args.Source.StartsWith(mqttChannelPairing))
                {
                    ProcessPairingMessage(args.Message);
                }
                else if (args.Source.StartsWith(mqttChannelChats))
                {
                    ProcessChatMessage(args.Message);
                }
            }
            catch (Exception e)
            {
                Debug.WriteLine(Constants.SOMETHING_WENT_WRONG, e);
            }
        }

        private void ProcessChatMessage(string message)
        {
            try
            {
                Debug.WriteLine("\n");
                Debug.WriteLine($"{RECEIVED_CHAT_MESSAGE}\n{message}");
                Debug.WriteLine("\n");

                if (ActivePersona == null)
                {
                    Debug.WriteLine(Constants.NO_ACTIVE_PERSONA);
                    return;
                }

                var myId = GetMyUri();

                var chatMessage = JsonConvert.DeserializeObject<ChatMessage>(message);

                if (chatMessage == null)
                {
                    Debug.WriteLine(ERROR_PARSING_CHAT_MESSAGE);
                    return;
                }

                if (MessageAlreadyRecieved(chatMessage.Id))
                {
                    // Don't process if we already did
                    return;
                }

                var msg = StringUtils.FromBase64Encoding(chatMessage.Msg);
                chatMessage.Msg = msg;

                Debug.WriteLine("\n");
                Debug.WriteLine($"{RECEIVED_CHAT_MESSAGE}\n{msg}");
                Debug.WriteLine("\n");

                if (keychainService.FindContact(chatMessage.SenderId) == null)
                {
                    Debug.WriteLine($"{UNRECOGNIZED_SENDER}: {chatMessage.SenderId}");
                    return;
                }

                IChatRepository chatRepository = SQLiteDBService.GetDBService();
                var chat = chatRepository.GetChat(chatMessage.SenderId, chatMessage.ReceiverId);

                if (chat == null)
                {
                    chat = new()
                    {
                        ParticipantIds = $"{chatMessage.SenderId},{chatMessage.ReceiverId}",
                        LastMsg = chatMessage.Msg,
                        Timestamp = DateTime.Now,
                    };
                }

                var recordId = chatRepository.SaveChat(chat);

                if (string.IsNullOrWhiteSpace(recordId))
                {
                    Debug.WriteLine(ERROR_SAVING_CHAT);
                    return;
                }
                else
                {
                    Debug.WriteLine($"{CHAT_SAVED_OR_UPDATED} {recordId}");
                }

                var encryptedMessage = chatMessage.Msg;

                if (string.IsNullOrWhiteSpace(encryptedMessage))
                {
                    Debug.WriteLine(RECEIVED_EMPTY_MESSAGE);
                    return;
                }

                recordId = chatRepository.SaveMessage(chatMessage.Id,
                                                      chatMessage.SenderId,
                                                      encryptedMessage,
                                                      ChatDirection.Receive,
                                                      chat,
                                                      null);
                if (recordId == null)
                {
                    Debug.WriteLine(Constants.ERROR_SAVING_MESSAGE);
                    return;
                }

                var decryptedMessage = keychainService.DecryptThenVerify(encryptedMessage);

                if (!string.IsNullOrWhiteSpace(decryptedMessage))
                {
                    DisplayMessage(chatMessage.SenderId,
                                   chatMessage.ReceiverId,
                                   decryptedMessage);
                }
                else
                {
                    Debug.WriteLine("Unable to decrypt message.");
                }
            }
            catch (Exception e)
            {
                Debug.WriteLine(Constants.SOMETHING_WENT_WRONG, e);
            }
        }

        private bool MessageAlreadyRecieved(string? id)
        {
            if (string.IsNullOrWhiteSpace(id))
            {
                throw new ArgumentNullException(nameof(id));
            }

            IChatRepository chatRepository = SQLiteDBService.GetDBService();
            return chatRepository.GetMessage(id) != null;
        }

        private void ProcessPairingMessage(string message)
        {
            try
            {
                Debug.WriteLine($"Received pairing message: {message}");

                var pairingMessage = JsonConvert.DeserializeObject<PairingMessage>(message);

                if (pairingMessage == null)
                {
                    ShowError("Unable to parse pairing message.");
                    return;
                }

                if (ActivePersona == null)
                {
                    ShowError($"Cannot pair. {Constants.NO_ACTIVE_PERSONA}");
                    return;
                }

                var persona = ActivePersona;
                string? myUri = StringUtils.GetUriString(persona.getUri());

                if (myUri != pairingMessage.receiverId)
                {
                    Debug.WriteLine($"Received {pairingMessage.msgType}. But it is not for me. Ignoring it!");
                    return;
                }

                Debug.WriteLine($"Received pair {pairingMessage.msgType}");

                switch (pairingMessage.msgType)
                {
                    case MessageType.PairRequest:
                        var response = ChannelMessage.MakePairResponse(persona, pairingMessage);

                        if (response == null)
                        {
                            Debug.WriteLine($"{Constants.ERROR_MAKING_PAIRING_RESPONSE}");
                            return;
                        }

                        var json = JsonConvert.SerializeObject(response);

                        if (json == null)
                        {
                            Debug.WriteLine(Constants.ERROR_CONVERTING_RESPONSE_TO_JSON);
                            return;
                        }

                        Debug.WriteLine($"{SENDING_PAIR_RESPONSE}: {json}" );

                        SendToMqtt($"{mqttChannelPairing}{response.receiverId}", json);

                        break;

                    case MessageType.PairResponse:
                    case MessageType.PairAck:
                        // Don't pair with self
                        if (Personas.ContainsKey(pairingMessage.senderId))
                        {
                            break;
                        }

                        // I received a response to my request, so add the responder to my contact list
                        string firstName = pairingMessage.senderName;
                        string lastName = pairingMessage.senderSubName;

                        var contact = keychainService.CreateContact(firstName,
                                                      lastName,
                                                      new Uri(Encoding.UTF8.GetBytes(pairingMessage.senderId)));

                        if (contact == null)
                        {
                            Debug.WriteLine($"{Constants.ERROR_CREATING_CONTACT_FOR} {firstName} {lastName}");
                            return;
                        }

                        _ = SavePlatformUserIsNotExists(firstName,
                                                                   lastName,
                                                                   PersonaStatus.Confirmed,
                                                                   pairingMessage.senderId,
                                                                   false);

                        Debug.WriteLine($"{SAVED_PAIRED_CONTACT_TO_CHAT_DB}{firstName} {lastName}");

                        AddContact(contact);

                        if (pairingMessage.msgType == MessageType.PairResponse)
                        {
                            var ack = ChannelMessage.MakePairAck(persona, pairingMessage);

                            if (ack == null)
                            {
                                Debug.WriteLine(Constants.ERROR_MAKING_PAIRING_ACK);
                                return;
                            }

                            var jsonAck = JsonConvert.SerializeObject(ack);

                            if (jsonAck == null)
                            {
                                Debug.WriteLine(ERROR_CONVERTING_PAIRING_ACK_TO_JSON);
                                return;
                            }


                            Debug.WriteLine(SENDING_PAIR_ACK);

                            SendToMqtt($"{mqttChannelPairing}{ack.receiverId}",
                                       jsonAck);
                        }

                        break;

                    default:
                        break;
                }
            }
            catch (Exception e)
            {
                Debug.WriteLine(Constants.SOMETHING_WENT_WRONG, e);
            }
        }

        private void SendToMqtt(string topic, string message)
        {
            if (MqttConnectionStatus != ConnectionStatus.CONNECTED)
            {
                throw new Exception(Constants.MQTT_NOT_CONNECTED);
            }

            if (ActivePersona == null)
            {
                throw new Exception(Constants.NO_ACTIVE_PERSONA);
            }

            mqttService.Send(topic, message);
        }

        private void DisplayMessage(string senderId, string recieverId, string decryptedMessage)
        {
            Application.Current?.Dispatcher.BeginInvoke(() =>
            {
                if (SelectedContact == null || ActivePersona == null)
                {
                    return;
                }

                if (SelectedContact.Uri != ALL &&
                    SelectedContact.Uri != senderId &&
                    SelectedContact.Uri != recieverId)
                {
                    return;
                }

                bool isFromMe = GetMyUri() == senderId;

                string firstName;
                string lastName;
                
                if (selectedContact.Uri == ALL)
                {
                    var contact = Contacts.GetValueOrDefault(senderId);

                    if (contact == null)
                    {
                        firstName = isFromMe ? ActivePersona.getName() : ALL;
                        lastName = isFromMe ? ActivePersona.getSubName() : string.Empty;
                    }
                    else
                    {
                        firstName = contact.getName();
                        lastName = contact.getSubName();
                    }
                }
                else
                {
                    var contact = Contacts.GetValueOrDefault(selectedContact.Uri);

                    if (contact == null)
                    {
                        ShowError(Constants.ERROR_FINDING_CONTACT);
                        return;
                    }

                    firstName = isFromMe ? ActivePersona.getName() : contact.getName();
                    lastName = isFromMe ? ActivePersona.getSubName() : contact.getSubName();
                }

                Messages.Add(new MessageModel()
                {
                    FirstName = firstName,
                    LastName = lastName,
                    Message = decryptedMessage,
                    IsFromMe = isFromMe,
                    Time = DateTime.Now,
                });

                ScrollToEnd(Messages);
            });
        }

        private void ScrollToEnd(ObservableCollection<MessageModel> source)
        {
            Application.Current?.Dispatcher.BeginInvoke(() =>
            {
                ChatListView.SelectedIndex = source.Count - 1;
                ChatListView.ScrollIntoView(ChatListView.SelectedItem);
            });
        }

        private void SubscribeToMqttTopic(Persona persona)
        {
            try
            {
                var uri = StringUtils.GetUriString(persona.getUri());

                // Subscribe to pairing, my chats, and all chats channels

                List<string> topics = GetMyTopics(uri);

                mqttService.Subscribe(topics);
            }
            catch (Exception ex)
            {
                Debug.WriteLine(Constants.SOMETHING_WENT_WRONG, ex);
            }
        }

        private List<string> GetMyTopics(string? uri)
        {
            return new List<string>
                {
                    $"{mqttChannelPairing}{uri}",
                    $"{mqttChannelChats}{uri}",
                    $"{mqttChannelChats}{ALL}"
                };
        }

        public void OnConnectionStatusChange(object? source, EventArgs? args)
        {
            if (args == null)
            {
                return;
            }

            var status = ((ConnectionStatusEventArgs)args).ConnectionStatus;

            if (MqttConnectionStatus == status)
            {
                return;
            }

            MqttConnectionStatus = status;

            if (status == ConnectionStatus.CONNECTED)
            {
                Debug.WriteLine(Constants.CONNECTED_TO_MQTT);
            }
        }
    }
}
