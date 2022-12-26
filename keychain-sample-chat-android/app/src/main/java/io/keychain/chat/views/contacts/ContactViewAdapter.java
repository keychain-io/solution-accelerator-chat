package io.keychain.chat.views.contacts;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

import io.keychain.chat.models.chat.User;
import io.keychain.core.Contact;
import io.keychain.chat.R;

public class ContactViewAdapter extends ArrayAdapter<User> {
    private final int layoutResource;
    private List<User> list;

    public ContactViewAdapter(Context context, int layoutResource, List<User> userList) {
        super(context, layoutResource, userList);
        this.list = userList;
        this.layoutResource = layoutResource;
    }

    @Override
    public int getCount() {
        return list == null ? 0 : list.size();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        View view = convertView;

        if (view == null) {
            LayoutInflater layoutInflater = LayoutInflater.from(getContext());
            view = layoutInflater.inflate(layoutResource, null);
        }

        User user = getItem(position);

        if (user != null) {
            TextView leftTextView = view.findViewById(R.id.nameText);
            TextView rightTextView = view.findViewById(R.id.subNameText);

            try {
                if (leftTextView != null) {
                    leftTextView.setText(user.firstName);
                }
                if (rightTextView != null) {
                    rightTextView.setText(user.lastName);
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        return view;
    }
}