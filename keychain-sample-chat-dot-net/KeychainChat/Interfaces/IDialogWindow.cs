﻿using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace KeychainChat.Interfaces
{
    public interface IDialogWindow
    {
        bool? DialogResult { get; set; }

        object DataContext { get; set; }

        bool? ShowDialog();
    }
}
