package mx.com.quiin.contactpicker.ui;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.MultiAutoCompleteTextView;
import android.widget.Toast;

import com.hootsuite.nachos.NachoTextView;
import com.hootsuite.nachos.chip.Chip;
import com.hootsuite.nachos.chip.ChipSpan;
import com.hootsuite.nachos.terminator.ChipTerminatorHandler;
import com.hootsuite.nachos.tokenizer.SpanChipTokenizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeSet;

import mx.com.quiin.contactpicker.Contact;
import mx.com.quiin.contactpicker.ContactChipCreator;
import mx.com.quiin.contactpicker.R;
import mx.com.quiin.contactpicker.SimpleContact;
import mx.com.quiin.contactpicker.adapters.AutoCompleteAdapter;
import mx.com.quiin.contactpicker.adapters.ContactAdapter;
import mx.com.quiin.contactpicker.interfaces.ContactSelectionListener;
import mx.com.quiin.contactpicker.views.CPLinearLayoutManager;

/**
 * A placeholder fragment containing a simple view.
 */
public class ContactPickerFragment extends Fragment
        implements
        LoaderManager.LoaderCallbacks<Cursor>,
        ContactSelectionListener {

    private String[] projection;
    private String selection;
    private String[] selectionArgs;
    private String sortBy;
    private int maxSelectedContacts;
    private boolean selectDisplayName;

    private static final int CONTACT_LOADER_ID = 666;
    private static final String SELECTION_SAVE = "SELECTION_SAVE";
    private static final String TAG = ContactPickerFragment.class.getSimpleName();

    private RecyclerView mRecyclerView;
    private NachoTextView mNachoTextView;
    private ContactAdapter mContactAdapter;
    private ArrayList<Contact> mContacts = new ArrayList<>();
    private final ArrayList<Contact> mSuggestions = new ArrayList<>();
    private ContactPickerActivity mActivity;

    private int mChipSize = 0;

    /*** Fragment callbacks ***/

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if(savedInstanceState != null){
            ArrayList<Contact> restored = (ArrayList<Contact>) savedInstanceState.getSerializable(SELECTION_SAVE);
            if(restored != null)
                mContacts = restored;
        }

        //Start contact cursor query in background
        mActivity = (ContactPickerActivity) getActivity();
        init();
        mActivity.getSupportLoaderManager().initLoader(CONTACT_LOADER_ID, new Bundle(), this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
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

        this.mNachoTextView.setChipTokenizer(new SpanChipTokenizer<>(getContext(), new ContactChipCreator(), ChipSpan.class));
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if(mContactAdapter != null)
            outState.putSerializable(SELECTION_SAVE, mContacts);
        else
            Log.e(TAG, "onSaveInstanceState: adapter is null");
    }

    /*** Loader callbacks ***/

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {

        return new CursorLoader(getContext(),
                ContactsContract.Data.CONTENT_URI, // URI
                projection, // projection fields
                selection, // the selection criteria
                selectionArgs, // the selection args
                sortBy // the sort order
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
        AutoCompleteAdapter adapter = new AutoCompleteAdapter(getContext(),R.layout.cp_suggestion_row, mSuggestions, selectDisplayName);
        this.mNachoTextView.setAdapter(adapter);

        this.mNachoTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                validateNewChip();
            }
        });
    }

    /**
     * Duplicate chip + max selection check
     */
    private void validateNewChip() {
        // get last chip and check if it already exists, or goes over maxSelectedContacts limit
        List<Chip> chips = mNachoTextView.getAllChips();
        int chipSize = chips.size();

        // only check if we're sure that there are new chips
        // TODO handle chips deleted directly
        if (chipSize > mChipSize) {
            mChipSize = chipSize;

            if (chipSize > 0) {
                Chip lastChip = chips.get(chipSize - 1);

                if (lastChip != null) {
                    // check if lastChip already exists
                    if (chips.size() > 1) {
                        for (Chip chip : chips.subList(0, chipSize - 1)) {
                            if (chip.getText().toString().equals(lastChip.getText().toString())) {
                                removeChip(lastChip);
                                return;
                            }
                        }
                    }

                    // doesn't exist, but check if we've reached max selections
                    if (maxSelectedContacts > 0 && chips.size() > maxSelectedContacts) {
                        removeChip(lastChip);
                        onMaxContactsReached();
                    }

                    // make sure it's selected (if available)
                    Contact contact = (Contact)lastChip.getData();

                    if (contact != null) {
                        mContactAdapter.selectContact(contact);
                    }
                }
            }

            hideKeyboard();
        }
    }

    private void hideKeyboard() {
        final InputMethodManager imm = (InputMethodManager) mActivity.getSystemService(Context.INPUT_METHOD_SERVICE);

        try {
            imm.hideSoftInputFromWindow(getView().getWindowToken(), 0);
        } catch (NullPointerException npe) {}
    }

    /**
     * This method is triggered when the loader is being reset
     * and the loader data is no longer available. Called if the data
     * in the provider changes and the Cursor becomes stale.
     */
    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
    }

    /*** Contact selection callbacks ***/

    /**
     * Called whenever a contact is selected from the {@code mRecyclerView}
     * This callback is NOT triggered when a {@code Chip} is added to {@code mNachoTextView}
     * @param contact Selected contact
     * @param communication Selected contact communication
     */
    @Override
    public void onContactSelected(Contact contact, String communication) {
        if (selectDisplayName) {
            addChip(contact.getDisplayName());
        } else {
            addChip(communication);
        }
    }

    /**
     * Check to see whether we already have max contacts selected
     * @return
     */
    @Override
    public boolean canSelectContact() {
        if (maxSelectedContacts == 0 || mNachoTextView.getAllChips().size() < maxSelectedContacts) {
            return true;
        }

        onMaxContactsReached();
        return false;
    }

    /**
     * Called whenever a contact is unselected from the {@code mRecyclerView}
     * This callback is NOT triggered when a {@code Chip} is added to {@code mNachoTextView}
     * @param contact Unselected contact
     * @param communication Unelected contact communication
     */
    @Override
    public void onContactDeselected(Contact contact, String communication) {
        if (selectDisplayName) {
            removeChipByIdentifier(contact.getDisplayName());
        } else {
            removeChipByIdentifier(communication);
        }
    }

    private void removeChipByIdentifier(String identifier) {
        Chip chip = getChipByIdentifier(identifier);

        if (chip != null) {
            removeChip(chip);
        }
    }

    private Chip getChipByIdentifier(String identifier) {
        for (Chip chip : mNachoTextView.getAllChips()) {
            if (chip.getText().equals(identifier)) {
                return chip;
            }
        }

        return null;
    }

    /**
     * Called when max number of contacts, as defined by CP_MAX_SELECTIONS reached
     */
    public void onMaxContactsReached() {
        Toast.makeText(getContext(),
                String.format(getResources().getQuantityString(R.plurals.max_contacts_selected, maxSelectedContacts), maxSelectedContacts),
                Toast.LENGTH_LONG).show();
    }

    /*** Private methods ***/


    private void setRecyclerView() {
        String selectedHex = mActivity.getSelectedColor();
        byte [] selectedDrawable = mActivity.getSelectedDrawable();
        byte [] unselectedDrawable = mActivity.getUnSelectedDrawable();
        this.mContactAdapter = new ContactAdapter(getContext(), mContacts, this, selectedHex, selectedDrawable, unselectedDrawable);
        mRecyclerView.swapAdapter(mContactAdapter, true);
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
        mRecyclerView.addItemDecoration(new DividerItemDecoration(getContext(), LinearLayoutManager.VERTICAL));
        mRecyclerView.setLayoutManager(new CPLinearLayoutManager(getContext()));
        mRecyclerView.stopScroll();
        mContactAdapter.notifyDataSetChanged();
    }

    private void addChip(String communication) {
        if(mNachoTextView != null){
            for (Chip chip : mNachoTextView.getAllChips()) {
                if (chip.getText().toString().equals(communication)) {
                    // already exists
                    return;
                }
            }

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

    private void init() {
        if(mActivity.isShowChips())
            this.mNachoTextView.setVisibility(View.VISIBLE);
        else
            this.mNachoTextView.setVisibility(View.GONE);

        this.selectDisplayName = mActivity.getSelectDisplayName();

        if (selectDisplayName)
            this.mNachoTextView.addChipTerminator('\n', ChipTerminatorHandler.BEHAVIOR_CHIPIFY_ALL);
        else
            this.mNachoTextView.addChipTerminator(' ', ChipTerminatorHandler.BEHAVIOR_CHIPIFY_TO_TERMINATOR);

        this.projection = mActivity.getProjection();
        this.selection = mActivity.getSelect();
        this.maxSelectedContacts = mActivity.getMaxSelectedContacts();
        this.selectionArgs = mActivity.getSelectArgs();
        this.sortBy = mActivity.getSortBy();
    }

    /*** Public methods ***/

    public TreeSet<SimpleContact> getSelected(){
        TreeSet<SimpleContact> toReturn = new TreeSet<>();
        if(this.mNachoTextView != null){

            ArrayList<SimpleContact> selectedContacts = new ArrayList<>();

            for (Chip chip : this.mNachoTextView.getAllChips()) {
                String text = chip.getText().toString();
                Contact contact = (Contact)chip.getData();

                if (contact != null) {
                    selectedContacts.add(new SimpleContact(contact.getDisplayName(), contact.getSelectedCommunication()));
                } else {
                    selectedContacts.add(new SimpleContact(text, text));
                }
            }

            if (selectDisplayName) {
                HashMap<String, SimpleContact> selected = this.mContactAdapter.getSelectionKeyedByDisplayName();

                for (SimpleContact simpleContact : selectedContacts) {
                    if(selected.containsKey(simpleContact.getDisplayName()))
                        toReturn.add(selected.get(simpleContact.getDisplayName()));
                    else
                        toReturn.add(simpleContact);
                }
            } else {
                HashMap<String, SimpleContact> selected = this.mContactAdapter.getSelectionKeyedByCommunication();

                for (SimpleContact simpleContact : selectedContacts) {
                    if(selected.containsKey(simpleContact.getCommunication()))
                        toReturn.add(selected.get(simpleContact.getCommunication()));
                    else
                        toReturn.add(simpleContact);
                }
            }
        }
        return toReturn;
    }

}
