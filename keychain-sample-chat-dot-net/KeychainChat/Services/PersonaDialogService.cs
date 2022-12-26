using System;
using KeychainChat.Interfaces;
using KeychainChat.MVVM.View;
using KeychainChat.MVVM.ViewModel;

namespace KeychainChat.Services
{
    public class PersonaDialogService : IDialogService
    {
        public T OpenDialog<T>(DialogViewModelBase<T> viewModel)
        {
            IDialogWindow window = new CreatePersonaWindow();
            window.DataContext = viewModel;
            window.ShowDialog();

            return viewModel.DialogResult;
        }
    }
}
