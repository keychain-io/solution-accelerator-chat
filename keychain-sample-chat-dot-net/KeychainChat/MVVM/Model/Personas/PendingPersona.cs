using System;
using System.Collections.Generic;
using System.Drawing;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using Keychain;

namespace KeychainChat.MVVM.Model.Personas
{
    public class PendingPersona
    {
        public Persona? persona;
        public IEnumerable<byte>? image;        // For future use
        public string recordId;

        public PendingPersona(string id, Persona? persona, IEnumerable<byte>? image)
        {
            recordId = id;
            this.persona = persona;
            this.image = image;
        }

    }
}
