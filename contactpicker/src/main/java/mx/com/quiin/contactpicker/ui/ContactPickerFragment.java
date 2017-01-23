package mx.com.quiin.contactpicker.ui;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.MultiAutoCompleteTextView;
import android.widget.SimpleAdapter;


import com.hootsuite.nachos.NachoTextView;
import com.hootsuite.nachos.chip.Chip;
import com.hootsuite.nachos.chip.ChipSpan;
import com.hootsuite.nachos.terminator.ChipTerminatorHandler;
import com.hootsuite.nachos.tokenizer.ChipTokenizer;
import com.hootsuite.nachos.tokenizer.SpanChipTokenizer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import mx.com.quiin.contactpicker.ContactChipCreator;
import mx.com.quiin.contactpicker.SimpleContact;
import mx.com.quiin.contactpicker.adapters.AutoCompleteAdapter;
import mx.com.quiin.contactpicker.adapters.ContactAdapter;
import mx.com.quiin.contactpicker.R;
import mx.com.quiin.contactpicker.Contact;
import mx.com.quiin.contactpicker.interfaces.ContactSelectionListener;

/**
 * A placeholder fragment containing a simple view.
 */
public class ContactPickerFragment extends Fragment implements
        LoaderManager.LoaderCallbacks<Cursor>,ContactSelectionListener {

    private static final int CONTACT_LOADER_ID = 666;
    private static final String TAG = ContactPickerFragment.class.getSimpleName();
    private RecyclerView mRecyclerView;
    private NachoTextView mNachoTextView;
    private ContactAdapter mContactAdapter;
    private List<Contact> mContacts = new ArrayList<>();
    private final ArrayList<Contact> mSuggestions = new ArrayList<>();

    private final String[] PROJECTION = new String[] {
            ContactsContract.Data._ID,
            ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.Data.MIMETYPE,
            ContactsContract.Contacts.PHOTO_URI,
    };

    public ContactPickerFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_contact_picker, container, false);
    }



    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        this.mRecyclerView = (RecyclerView) view.findViewById(R.id.cp_listView);
        this.mNachoTextView = (NachoTextView) view.findViewById(R.id.nachoTextView);

        this.mNachoTextView.setTokenizer(new MultiAutoCompleteTextView.CommaTokenizer());
        this.mNachoTextView.setThreshold(3);
        this.mNachoTextView.setMaxLines(2);
        this.mNachoTextView.addChipTerminator(' ', ChipTerminatorHandler.BEHAVIOR_CHIPIFY_TO_TERMINATOR);
        this.mNachoTextView.setChipTokenizer(new SpanChipTokenizer<>(getContext(), new ContactChipCreator(), ChipSpan.class));


        getActivity().getSupportLoaderManager()
                .initLoader(CONTACT_LOADER_ID,
                        new Bundle(),
                        this);
    }

    private void setRecyclerView() {
        this.mContactAdapter = new ContactAdapter(getContext(), mContacts, this);
        mRecyclerView.setAdapter(mContactAdapter);
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
        mRecyclerView.addItemDecoration(new DividerItemDecoration(getContext(), LinearLayoutManager.VERTICAL));
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // Define the columns to retrieve

        String select =  "(" + ContactsContract.Data.MIMETYPE + "=? OR "
                + ContactsContract.Data.MIMETYPE + "=?)";

        String[] selectionArgs = new String[]{
                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
        };

        String sort = ContactsContract.Contacts.DISPLAY_NAME + " ASC";
        // Return the loader for use
        return new CursorLoader(getContext(),
                ContactsContract.Data.CONTENT_URI, // URI
                PROJECTION, // projection fields
                select, // the selection criteria
                selectionArgs, // the selection args
                sort // the sort order
        );
    }

    /**
     * When the system finishes retrieving the Cursor through the CursorLoader,
     * a call to the onLoadFinished() method takes place.
     **/
    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        if(cursor.moveToFirst()){
            do{
                String displayName = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
                String communication = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                Contact newContact = new Contact(displayName);

                Contact suggestion = new Contact(displayName);
                suggestion.addCommunication(communication);
                mSuggestions.add(suggestion);

                if(!mContacts.contains(newContact)){
                    newContact.addCommunication(communication);
                    mContacts.add(newContact);
                }else{
                    Contact existingContact = mContacts.get(mContacts.indexOf(newContact));
                    existingContact.addCommunication(communication);
                }

            }while(cursor.moveToNext());
        }
        if(mContactAdapter != null) {
            mContactAdapter.notifyDataSetChanged();
        }

        setRecyclerView();
        AutoCompleteAdapter adapter = new AutoCompleteAdapter(getContext(),R.layout.cp_suggestion_row, mSuggestions);
        this.mNachoTextView.setAdapter(adapter);
    }

    /**
     * This method is triggered when the loader is being reset
     * and the loader data is no longer available. Called if the data
     * in the provider changes and the Cursor becomes stale.
     */
    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
    }

    private void addChip(String communication) {
        if(mNachoTextView != null){
            mNachoTextView.append(communication);
            int start = mNachoTextView.getText().toString().indexOf(communication);
            int last = mNachoTextView.getText().length();
            mNachoTextView.chipify(start,last);
        }else Log.e(TAG, "mNachoTextView is null");
    }

    private void removeChip(Chip toRemove){
        if(toRemove != null && mNachoTextView.getChipTokenizer() != null){
            mNachoTextView.getChipTokenizer().deleteChip(toRemove, mNachoTextView.getText());
        }
    }

    @Override
    public void onContactSelected(Contact contact, String communication) {
        addChip(communication);
    }

    @Override
    public void onContactDeselected(Contact contact, String communication) {
        Chip toRemove = null;
        for (Chip chip : mNachoTextView.getAllChips()) {
            if(chip.getText().equals(communication))
                toRemove = chip;
        }

        removeChip(toRemove);
    }

    public Set<SimpleContact> getSelected(){
        Set<SimpleContact> toReturn = new HashSet<>();
        if(this.mNachoTextView != null){
            Set<SimpleContact> chips = new HashSet<>();

            for (String s : this.mNachoTextView.getChipValues()) {
                chips.add(new SimpleContact(s,s));
            }

            List<SimpleContact> selected = this.mContactAdapter.getSelection();
            for (SimpleContact simpleContact : chips) {
                String communication = simpleContact.getCommunication();
                if(selected.contains(communication))
                    toReturn.add(simpleContact);
                else
                    toReturn.add(new SimpleContact(communication, communication));
            }
        }
        return toReturn;
    }
}
