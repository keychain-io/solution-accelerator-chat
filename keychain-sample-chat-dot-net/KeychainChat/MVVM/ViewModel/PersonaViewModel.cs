using System.Windows.Input;
using GalaSoft.MvvmLight.Command;
using KeychainChat.Interfaces;
using KeychainChat.MVVM.Model.Personas;
using KeychainChat.Services;
using KeychainChat.Services.Database;
using NLog;

namespace KeychainChat.MVVM.ViewModel
{
    public class PersonaViewModel : DialogViewModelBase<CreatePersonaDialogResult>
    {
        private static string TAG = "PersonaViewModel";

        private Logger log = LogManager.GetCurrentClassLogger();

        private string firstName;

        public string FirstName
        {
            get => firstName;
            set
            {
                firstName = value;
                SaveButtonEnabled = !string.IsNullOrWhiteSpace(FirstName)
                    && !string.IsNullOrWhiteSpace(LastName);

                OnPropertyChanged();
            }
        }

        private string lastName;

        public string LastName
        {
            get => lastName;
            set
            {
                lastName = value;
                SaveButtonEnabled = !string.IsNullOrWhiteSpace(FirstName)
                    && !string.IsNullOrWhiteSpace(LastName);

                OnPropertyChanged();
            }
        }

        private bool saveButtonEnabled;

        public bool SaveButtonEnabled
        {
            get => saveButtonEnabled;
            set
            {
                saveButtonEnabled = value;
                OnPropertyChanged();
            }
        }

        private KeychainService keychainService = KeychainService.GetInstance();

        public ICommand SaveCommand { get; set; }

        public PersonaViewModel()
        {
            SaveCommand = new RelayCommand<IDialogWindow>(SavePersona);
        }

        private void SavePersona(IDialogWindow dialogWindow)
        {
            var dialogResult = new CreatePersonaDialogResult()
            {
                FirstName = FirstName,
                LastName = LastName,
            };

           CloseDialogWithResult(dialogWindow, dialogResult);
        }
    }
}
