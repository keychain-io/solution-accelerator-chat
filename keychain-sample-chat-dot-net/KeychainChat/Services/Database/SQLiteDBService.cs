using System;
using System.Collections.Generic;
using System.Configuration;
using System.Linq;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Infrastructure;

using KeychainChat.Interfaces;
using KeychainChat.Common;
using KeychainChat.Utils;
using NLog;
using KeychainChat.MVVM.Model.Chat;
using System.IO;
using Keychain;
using Exception = System.Exception;
using System.Diagnostics;
using static Microsoft.EntityFrameworkCore.DbLoggerCategory.Database;
using System.Windows;

namespace KeychainChat.Services.Database
{
    public class SQLiteDBService : DbContext, IChatRepository
    {
        private static string TAG = "SQLiteDBService";

        private Logger log = LogManager.GetCurrentClassLogger();

        private SQLiteDBService()
        {
            Database.EnsureCreated();
        }

        public static SQLiteDBService GetDBService()
        {
            var service = new SQLiteDBService();
            var connection = service.Database.GetDbConnection();

            connection.Open();

            using (var command = connection.CreateCommand())
            {
                command.CommandText = "PRAGMA journal_mode=Off;";
                command.ExecuteNonQuery();
            }

            return service;
        }
        
        protected override void OnConfiguring(DbContextOptionsBuilder optionsBuilder)
        {
            var dbPath = ConfigurationManager.AppSettings.Get("ChatDBPath");

            if (dbPath == null)
            {
                // When entity framework tries to create the database using
                // the commandline tool, the App.config file is not loaded
                // So for now, we'll default it.
                dbPath = @"C:\KeychainChat\data\chats.db";
            }

            var path = Path.GetFullPath(dbPath);
            var directory = Path.GetDirectoryName(path);

            if (directory == null)
            {
                throw new Exception($"DirectoryService cannot be depermined from chat DB path {path}");
            }

            if (!Directory.Exists(directory))
            {
                Directory.CreateDirectory(directory);
            }

            optionsBuilder.UseSqlite($"Data Source={path}");
        }

        public DbSet<User>? Users { get; set; }

        public DbSet<Chat>? Chats { get; set; }

        public DbSet<ChatMessage>? ChatMessages { get; set; }

        public void CreateDatabase() => new DatabaseFacade(this).EnsureCreated();

        public IList<ChatMessage> GetAllMessages(Chat chat)
        {
            if (chat is null)
            {
                throw new ArgumentNullException(nameof(chat));
            }

            Debug.WriteLine($"Getting all messages for chat ID: {chat.Id}");

            try
            {
                if (ChatMessages != null)
                {
                    if (chat.ParticipantIds.EndsWith(Constants.ALL))
                    {
                        return ChatMessages.Where(m => m.ReceiverId == Constants.ALL).ToList();
                    }

                    return ChatMessages.Where(m => m.ChatId == chat.Id)
                                       .ToList();
                }
            }
            catch (Exception e)
            {
                Debug.WriteLine($"Error getting all messages for chat ID: {chat.Id}", e);
            }

            return new List<ChatMessage>();
        }

        public Chat? GetChat(string senderId, string receiverId)
        {
            Debug.WriteLine($"Getting chat for senderId, receiverId: {senderId}, {receiverId}");

            if (string.IsNullOrWhiteSpace(senderId))
            {
                throw new ArgumentException($"'{nameof(senderId)}' cannot be null or whitespace.", nameof(senderId));
            }

            if (string.IsNullOrWhiteSpace(receiverId))
            {
                throw new ArgumentException($"'{nameof(receiverId)}' cannot be null or whitespace.", nameof(receiverId));
            }

            try
            {
                var chat = Chats
                    .Where(c => c.ParticipantIds.Contains(senderId) && c.ParticipantIds.Contains(receiverId))
                    .FirstOrDefault();

                return chat;
            }
            catch (Exception e)
            {
                Debug.WriteLine($"Error getting chat for senderId, receiverId: {senderId}, {receiverId}", e);
                return null;
            }        
        }

        public ChatMessage? GetMessage(string recordId)
        {
            Debug.WriteLine($"Getting message for recordId: {recordId}");

            if (string.IsNullOrWhiteSpace(recordId))
            {
                throw new ArgumentException($"'{nameof(recordId)}' cannot be null or whitespace.", nameof(recordId));
            }

            try
            {
                return ChatMessages?.Where(m => m.Id == recordId)
                                    .FirstOrDefault();

            }
            catch (Exception e)
            {
                Debug.WriteLine($"Error getting message for recordId: {recordId}", e);
                return null;
            }
        }

        public User? GetPlatformUser(string recordId)
        {
            Debug.WriteLine($"Getting platform user for recordId: {recordId}");

            try
            {
                var queryable = Users.FromSqlInterpolated(
                    $"SELECT * FROM Users WHERE Id = {recordId}");

                return queryable.First();

            }
            catch (Exception e)
            {
                Debug.WriteLine($"Error getting message for recordId: {recordId}", e);
                return null;
            }
        }

        public User? GetPlatformUser(string firstName, string lastName)
        {
            Debug.WriteLine($"Getting platform user for: {firstName} {lastName}");

            if (string.IsNullOrWhiteSpace(firstName))
            {
                throw new ArgumentException($"'{nameof(firstName)}' cannot be null or whitespace.", nameof(firstName));
            }

            if (lastName == null)
            {
                throw new ArgumentException($"'{nameof(lastName)}' cannot be null or whitespace.", nameof(lastName));
            }

            try
            {
                return Users?.Where(u => u.FirstName == firstName && u.LastName == lastName)
                             .FirstOrDefault();
            }
            catch (Exception e)
            {
                Debug.WriteLine($"Error getting platform user for: {firstName} {lastName}", e);
                return null;
            }
        }

        public User? GetPlatformUserByUri(string uri)
        {
            Debug.WriteLine($"Getting platform user for: {uri}");

            if (string.IsNullOrWhiteSpace(uri))
            {
                throw new ArgumentException($"'{nameof(uri)}' cannot be null or whitespace.", nameof(uri));
            }

            try
            {
                return Users?.Where(u => u.Uri == uri)
                             .FirstOrDefault();
            }
            catch (Exception e)
            {
                Debug.WriteLine($"Error getting platform user for: {uri}", e);
                return null;
            }
        }

        public Dictionary<string, User> GetPlatformUsers(IReadOnlyCollection<string> filterBy)
        {
            Debug.WriteLine($"Getting platform user");

            if (filterBy is null)
            {
                throw new ArgumentNullException(nameof(filterBy));
            }

            try
            {
                if (Users != null)
                {
                    return Users.Where(u => filterBy.Contains(u.Uri))
                                .ToDictionary(u => u.GetKey(), u => u);
                }
            } 
            catch (Exception e)
            {
                Debug.WriteLine(Constants.ERROR_GETTING_PLATFORM_USERS, e);
            }

            return new Dictionary<string, User>();
        }

        public string? SaveChat(Chat chat)
        {
            if (chat is null)
            {
                throw new ArgumentNullException(nameof(chat));
            }

            var participants = chat.ParticipantIds.Split(',');

            if (participants.Length != 2)
            {
                throw new ArgumentException($"{Constants.ERROR_SAVING_CHAT_TO_DB} {Constants.PARTICIPANT_IDS_MUST_CONTAIN_2_ELEMENTS}",
                                            "chat");
            }

            Debug.WriteLine($"Saving chat for senderId/receiverId: {participants[0]}, {participants[1]}");

            try
            {
                if (Chats != null)
                {
                    var existing = GetChat(participants[0], participants[1]);

                    if (existing == null)
                    {
                        Chats.Add(chat);
                        SaveChanges(true);

                        return chat.Id;
                    }

                    existing.LastMsg = chat.LastMsg;
                    existing.Timestamp = chat.Timestamp;

                    Chats.Update(existing);
                    SaveChanges(true);

                    return existing.Id;
                }
            }
            catch (Exception e)
            {
                Debug.WriteLine($"Error saving chat", e);
            }

            return null;
        }

        public string? SaveMessage(string? chatMsgId, string senderId, string msg, string direction, Chat chat, IEnumerable<byte>? image)
        {
            if (string.IsNullOrWhiteSpace(senderId))
            {
                throw new ArgumentException($"'{nameof(senderId)}' cannot be null or whitespace.", nameof(senderId));
            }

            if (string.IsNullOrWhiteSpace(msg))
            {
                throw new ArgumentException($"'{nameof(msg)}' cannot be null or whitespace.", nameof(msg));
            }

            if (string.IsNullOrWhiteSpace(direction))
            {
                throw new ArgumentException($"'{nameof(direction)}' cannot be null or whitespace.", nameof(direction));
            }

            if (chat is null)
            {
                throw new ArgumentNullException(nameof(chat));
            }

            Debug.WriteLine($"Saving message for chat ID {chat.Id}");

            try
            {
                if (ChatMessages != null)
                {
                    var ids = chat.ParticipantIds.Split(",");

                    if (ids.Length != 2)
                    {
                        throw new Exception(Constants.PARTICIPANT_IDS_MUST_CONTAIN_2_ELEMENTS);
                    }

                    var receiverId = senderId == ids[0] ? ids[1] : ids[0];

                    var chatMsg = new ChatMessage()
                    {
                        Id = !string.IsNullOrWhiteSpace(chatMsgId) ? chatMsgId : Guid.NewGuid().ToString(),
                        ChatId = chat.Id,
                        SendOrRcvd = direction,
                        SenderId = senderId,
                        ReceiverId = receiverId,
                        Msg = msg,
                        Timestamp = DateUtils.UnixTimestamp()
                    };

                    ChatMessages.Add(chatMsg);
                    SaveChanges(true);

                    return chatMsg.Id;
                }
            }
            catch (Exception e)
            {
                Debug.WriteLine($"Error chat message", e);
            }

            return null;
        }

        public string? SavePhotoMessage(string senderUri, IEnumerable<byte> image, Chat chat)
        {
            throw new NotImplementedException();
        }

        public string? SaveUserProfile(string firstName, string lastName, PersonaStatus status, string uri)
        {
            if (string.IsNullOrWhiteSpace(firstName))
            {
                throw new ArgumentException($"'{nameof(firstName)}' cannot be null or whitespace.", nameof(firstName));
            }

            Debug.WriteLine($"Saving user profile for {firstName} " +
                $"{(!string.IsNullOrWhiteSpace(lastName) ? lastName : "")}");

            try
            {
                if (Users != null)
                {
                    // Image is not currently supported, but left for future use
                    var user = new User()
                    {
                        FirstName = firstName,
                        LastName = lastName,
                        Status = (int)status,
                        Uri = uri,
                    };

                    Users.Add(user);
                    SaveChanges(true);

                    Debug.WriteLine($"Successfully saved user profile for {firstName} " +
                        $"{(!string.IsNullOrWhiteSpace(lastName) ? lastName : "")}");

                    return user.Id;
                }
            }
            catch (Exception e)
            {
                Debug.WriteLine($"Error saving user profile", e);
            }

            return null;
        }

        public bool UpdateChat(string id, string lastMessage)
        {
            if (string.IsNullOrWhiteSpace(id))
            {
                throw new ArgumentException($"'{nameof(id)}' cannot be null or whitespace.", nameof(id));
            }

            if (string.IsNullOrWhiteSpace(lastMessage))
            {
                throw new ArgumentException($"'{nameof(lastMessage)}' cannot be null or whitespace.", nameof(lastMessage));
            }

            Debug.WriteLine($"Updating chat for chat ID: {id}");

            try
            {
                if (Chats != null)
                {
                    var chat = Chats.Find(id);

                    if (chat == null)
                    {
                        throw new KeyNotFoundException($"Cannot find chat with chat ID: {id}");
                    }

                    chat.LastMsg = lastMessage;
                    SaveChanges(true);

                    return true;
                }
            }
            catch (Exception e)
            {
                Debug.WriteLine($"Error updating chat for chat ID: {id}", e);
            }

            return false;
        }

        public bool UpdateUserProfile(string firstName, string lastName, PersonaStatus status, string? uri)
        {
            if (string.IsNullOrWhiteSpace(firstName))
            {
                throw new ArgumentException($"'{nameof(firstName)}' cannot be null or whitespace.", nameof(firstName));
            }

            if (string.IsNullOrWhiteSpace(uri))
            {
                throw new ArgumentException($"'{nameof(uri)}' cannot be null or whitespace.", nameof(uri));
            }

            Debug.WriteLine($"Updating user profile for {firstName} " +
                $"{(!string.IsNullOrWhiteSpace(lastName) ? lastName : "")}");

            try
            {
                if (Users != null)
                {
                    var user = GetPlatformUser(firstName, lastName);

                    if (user == null)
                    {
                        throw new Exception($"Cannot find user profile for {firstName} " +
                            $"{(!string.IsNullOrWhiteSpace(lastName) ? lastName : "")}");
                    }

                    user.Status = (int)status;
                    user.Uri = uri;
                    Users.Update(user);
                    SaveChanges(true);

                    return true;
                }
            }
            catch (Exception e)
            {
                Debug.WriteLine($"{Constants.ERROR_UPDATING_USER_PROFILE} {firstName} " +
                    $"{(!string.IsNullOrWhiteSpace(lastName) ? lastName : "")}", e);
            }

            return false;
        }

        public void DeletePlatformUser(User user)
        {
            if (user is null)
            {
                throw new ArgumentException($"'{nameof(user)}' cannot be null.");
            }

            string firstName = user.FirstName;
            string lastName = user.LastName;

            try
            {
                var pUser = GetPlatformUser(firstName, lastName);

                if (pUser == null)
                {
                    return;
                }

                Users.Remove(pUser);
                SaveChanges(true);
            }
            catch (Exception e)
            {
                Debug.WriteLine($"Error deleting user for {firstName} " +
                    $"{(!string.IsNullOrWhiteSpace(lastName) ? lastName : "")}", e);
            }
        }

        public void RenamePlatformUser(User user, string newFirstName, string newLastName)
        {
            if (user is null)
            {
                throw new ArgumentException($"'{nameof(user)}' cannot be null.");
            }

            string firstName = user.FirstName;
            string lastName = user.LastName;

            try
            {
                if (Users != null)
                {
                    var pUser = GetPlatformUser(firstName, lastName);

                    if (pUser == null)
                    {
                        throw new Exception($"Cannot find user profile for {firstName} " +
                            $"{(!string.IsNullOrWhiteSpace(lastName) ? lastName : "")}");
                    }

                    pUser.FirstName = newFirstName;
                    pUser.LastName = newLastName;
                    Users.Update(pUser);
                    SaveChanges(true);
                }
            }
            catch (Exception e)
            {
                Debug.WriteLine($"Error renaming user for {firstName} " +
                    $"{(!string.IsNullOrWhiteSpace(lastName) ? lastName : "")}", e);
            }
        }
    }
}
