using NLog;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

using Keychain;
using KeychainChat.Services;
using KeychainChat.Common;
using KeychainChat.MVVM.Model.Personas;
using System.Diagnostics;

namespace KeychainChat.MVVM.ViewModel
{
    public class AuthViewModel
    {
        private static string TAG = "AuthViewModel";

        private readonly Logger log = LogManager.GetCurrentClassLogger();

        public Persona? Persona { get; set; }

        private PersonaLoginStatus loginStatus = PersonaLoginStatus.None;

        private KeychainService keychainService = KeychainService.GetInstance();

        public EventHandler<LoginEventArgs> LoginStatusChanged;

        public bool IsLoginEnabled()
        {
            if (Persona == null)
            {
                return false;
            }

            try
            {
                return Persona.getStatus() == PersonaStatus.Confirmed;
            }
            catch (Exception e)
            {
                Debug.WriteLine(Constants.SOMETHING_WENT_WRONG, e);
                return false;
            }
        }

        public void setActivePersona()
        {
            if (!IsLoginEnabled() || Persona == null)
            {
                Debug.WriteLine("Attemped to set active persona and no persona was selected or login is disabled.");
                return;
            }

            try
            {
                Debug.WriteLine("Loggin in persona:");

                LoginStatus loginStatus = keychainService.SetActivePersona(Persona)
                    ? LoginStatus.Ok
                    : LoginStatus.Faulure;

                switch (loginStatus)
                {
                    case LoginStatus.Ok:
                        Debug.WriteLine("Login successful.");

                        break;

                    default:
                        Debug.WriteLine("Error setting active persona.");
                        break;
                }

                LoginStatusChanged?.Invoke(this, new LoginEventArgs(loginStatus));
            }
            catch (Exception e)
            {
                Debug.WriteLine("Error loggin in persona", e);
            }
        }
    }
}
