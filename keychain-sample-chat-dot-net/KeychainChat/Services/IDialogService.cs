using KeychainChat.MVVM.ViewModel;

namespace KeychainChat.Services
{
    public interface IDialogService
    {
        T OpenDialog<T>(DialogViewModelBase<T> viewModel);
    }
}
