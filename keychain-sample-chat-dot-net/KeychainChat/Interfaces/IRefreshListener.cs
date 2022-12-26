using System;

namespace KeychainChat.Interfaces
{
    public interface IRefreshListener
    {
        public void OnRefresh(object? source, EventArgs? args);
    }
}
