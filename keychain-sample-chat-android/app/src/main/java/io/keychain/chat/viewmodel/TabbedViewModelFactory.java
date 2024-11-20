package io.keychain.chat.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import io.keychain.chat.MqttUseCase;

public class TabbedViewModelFactory implements ViewModelProvider.Factory {

    private final Application application;
    private final MqttUseCase useCase;

    public TabbedViewModelFactory(Application application, MqttUseCase useCase) {
        this.application = application;
        this.useCase = useCase;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        return (T) new TabbedViewModel(application, useCase);
    }
}
