using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace KeychainChat.MVVM.Model.Personas
{
    public class LoginEventArgs : EventArgs
    {
        public LoginEventArgs(LoginStatus loginStatus)
        {
            LoginStatus = loginStatus;
        }

        public LoginStatus LoginStatus { get; set; }
    }
}
