package com.midburn.gate.midburngate.activities;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.midburn.gate.midburngate.HttpRequestListener;
import com.midburn.gate.midburngate.R;
import com.midburn.gate.midburngate.application.MainApplication;
import com.midburn.gate.midburngate.consts.AppConsts;
import com.midburn.gate.midburngate.model.Group;
import com.midburn.gate.midburngate.model.Ticket;
import com.midburn.gate.midburngate.utils.AppUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;

import okhttp3.HttpUrl;
import okhttp3.Response;

public class ShowActivity
		extends AppCompatActivity {

	private static final int PRODUCTION_EARLY_ARRIVAL_WORKAROUND = -1;
	private TextView     mTicketOrderNumberTextView;
	private TextView     mTicketNumberTextView;
	private TextView     mTicketOwnerNameTextView;
	private TextView     mTicketTypeTextView;
	private LinearLayout mDisabledLayout;
	private TextView     mTicketOwnerIdTextView;

	private Button      mEntranceButton;
	private Button      mExitButton;
	private Button      mCancelButton;
	private ProgressBar mProgressBar;

	private enum State {
		ERALY_ENTRANCE,
		MIDBURN
	}

	private enum Action {
		ENTER,
		EXIT
	}

	private State mState;
	private Action mAction;

	private HttpRequestListener mHttpRequestListener;

	private String mGateCode;
	private Ticket mTicket;
	private Group mSelectedGroup;

	public void exit(View view) {
		boolean hasInternetConnection = AppUtils.isConnected(this);
		if (!hasInternetConnection) {
			AppUtils.createAndShowDialog(this, getString(R.string.no_network_dialog_title), getString(R.string.no_network_dialog_message), getString(R.string.ok), null, null, null, android.R.drawable.ic_dialog_alert);
			return;
		}
		if (mTicket == null) {
			Log.e(AppConsts.TAG, "ticket is null");
			return;
		}

		mProgressBar.setVisibility(View.VISIBLE);

		String barcode = mTicket.getBarCode();
		Log.d(AppConsts.TAG, "user barcode to exit: " + barcode);

		HttpUrl url = new HttpUrl.Builder().scheme("https")
		                                   .host(AppConsts.SERVER_URL)
		                                   .addPathSegment("api")
		                                   .addPathSegment("gate")
		                                   .addPathSegment("gate-exit")
		                                   .build();

		JSONObject jsonObject = new JSONObject();
		try {
			jsonObject.put("event_id", mGateCode);
			jsonObject.put("barcode", barcode);
		} catch (JSONException e) {
			Log.e(AppConsts.TAG, e.getMessage());
		}

		AppUtils.doPOSTHttpRequest(url, jsonObject.toString(), mHttpRequestListener);
	}

	public void entrance(View view) {
		boolean hasInternetConnection = AppUtils.isConnected(this);
		if (!hasInternetConnection) {
			AppUtils.createAndShowDialog(this, getString(R.string.no_network_dialog_title), getString(R.string.no_network_dialog_message), getString(R.string.ok), null, null, null, android.R.drawable.ic_dialog_alert);
			return;
		}
		if (mTicket == null) {
			Log.e(AppConsts.TAG, "ticket is null");
			return;
		}
		if (mState.equals(State.ERALY_ENTRANCE)) {
			handleGroupTypes();
		}
		else if (mState.equals(State.MIDBURN)) {
			sendEntranceRequestWithoutGroups();
		}
		else {
			Log.e(AppConsts.TAG, "unknown state. mState: " + mState);
		}
	}

	private void handleGroupTypes() {
		final ArrayList<Group> groupsArrayList = mTicket.getGroups();

		//check if group type is production. if so, select it immediately
		if (mTicket.isProductionEarlyArrival) {
			sendEntranceRequest(PRODUCTION_EARLY_ARRIVAL_WORKAROUND);
			return;
		}

		// no groups alert
		if (groupsArrayList == null || groupsArrayList.size() == 0) {
			AppUtils.createAndShowDialog(this, "שגיאה", getString(R.string.no_early_arrival_message), getString(R.string.ok), null, null, null, android.R.drawable.ic_dialog_alert);
			return;
		}

		// show group selection dialog
		int groupsArrayListSize = groupsArrayList.size();
		CharSequence groupsArray[] = new CharSequence[groupsArrayListSize];
		for (int i = 0 ; i < groupsArrayListSize ; i++) {
			Group group = groupsArrayList.get(i);
			groupsArray[i] = getGroupType(group) + ": "+ group.getName();
		}
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("בחר קבוצה");
		builder.setItems(groupsArray, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				Group selectedGroup = groupsArrayList.get(which);
				Log.d(AppConsts.TAG, selectedGroup.getName() + " was clicked. id: " + selectedGroup.getId());
				mProgressBar.setVisibility(View.VISIBLE);
				mSelectedGroup = selectedGroup;
				sendEntranceRequest(selectedGroup.getId());
			}
		});
		builder.show();
	}

	private String getGroupType(Group group) {
		if (group.getType() == null) {
			return "";
		}

		if (group.getType().equals(AppConsts.GROUP_TYPE_ART)) {
			return "מיצב";
		}

		if (group.getType().equals(AppConsts.GROUP_TYPE_CAMP)) {
			return "מחנה";
		}

		if (group.getType().equals(AppConsts.GROUP_TYPE_PRODUCTION)) {
			return "הפקה";
		}

		return "";
	}

	private void sendEntranceRequestWithoutGroups() {
		String barcode = mTicket.getBarCode();
		Log.d(AppConsts.TAG, "user barcode to enter: " + barcode);
		JSONObject jsonObject = new JSONObject();
		try {
			jsonObject.put("barcode", barcode);
			jsonObject.put("event_id", mGateCode);
		} catch (JSONException e) {
			Log.e(AppConsts.TAG, e.getMessage());
		}
		HttpUrl url = new HttpUrl.Builder().scheme("https")
		                                   .host(AppConsts.SERVER_URL)
		                                   .addPathSegment("api")
		                                   .addPathSegment("gate")
		                                   .addPathSegment("gate-enter")
		                                   .build();

		AppUtils.doPOSTHttpRequest(url, jsonObject.toString(), mHttpRequestListener);
	}

	private void sendEntranceRequest(int groupId) {
		String barcode = mTicket.getBarCode();
		Log.d(AppConsts.TAG, "user barcode to enter: " + barcode);
		JSONObject jsonObject = new JSONObject();
		try {
			jsonObject.put("barcode", barcode);
			jsonObject.put("event_id", mGateCode);
			// TODO(alex): 5/7/18 this is a workaround -1 means this is a production early arrival. server will validate that if no group is present
			if (groupId != PRODUCTION_EARLY_ARRIVAL_WORKAROUND) {
				jsonObject.put("group_id", groupId);
			}
		} catch (JSONException e) {
			Log.e(AppConsts.TAG, e.getMessage());
		}
		HttpUrl url = new HttpUrl.Builder().scheme("https")
		                                   .host(AppConsts.SERVER_URL)
		                                   .addPathSegment("api")
		                                   .addPathSegment("gate")
		                                   .addPathSegment("gate-enter")
		                                   .build();

		AppUtils.doPOSTHttpRequest(url, jsonObject.toString(), mHttpRequestListener);
	}

	public void cancel(View view) {
		Intent intent = new Intent(ShowActivity.this, MainActivity.class);
		startActivity(intent);
	}

	private void handleServerResponse(final Response response) {
		MainApplication.getsMainThreadHandler()
		               .post(() -> {
			               if (response == null) {
				               Log.e(AppConsts.TAG, "response is null");
				               AppUtils.playMusic(ShowActivity.this, AppConsts.ERROR_MUSIC);
				               AppUtils.createAndShowDialog(ShowActivity.this, "פעולה נכשלה", null, getString(R.string.ok), null, null, null, android.R.drawable.ic_dialog_alert);
				               return;
			               }
			               try {
				               String responseBodyString = response.body()
				                                                   .string();
				               Log.d(AppConsts.TAG, "response.body():" + responseBodyString);
				               if (response.code() == AppConsts.RESPONSE_OK) {
					               JSONObject jsonObject = new JSONObject(responseBodyString);
					               AppUtils.playMusic(ShowActivity.this, AppConsts.OK_MUSIC);
					               String resultMessage = (String) jsonObject.get("message");
					               Log.d(AppConsts.TAG, "resultMessage: " + resultMessage);
								   showConfirmationAlert();
				               }
				               else {
					               Log.e(AppConsts.TAG, "response code: " + response.code() + " | response body: " + responseBodyString);
					               AppUtils.playMusic(ShowActivity.this, AppConsts.ERROR_MUSIC);
					               JSONObject jsonObject = new JSONObject(responseBodyString);
					               String errorMessage = (String) jsonObject.get("error");
					               AppUtils.createAndShowDialog(ShowActivity.this, "שגיאה", AppUtils.getErrorMessage(ShowActivity.this, errorMessage), getString(R.string.ok), null, null, null, android.R.drawable.ic_dialog_alert);
				               }
			               } catch (IOException | JSONException e) {
				               Log.e(AppConsts.TAG, e.getMessage());
				               AppUtils.playMusic(ShowActivity.this, AppConsts.ERROR_MUSIC);
				               AppUtils.createAndShowDialog(ShowActivity.this, "שגיאה", e.getMessage(), getString(R.string.ok), null, null, null, android.R.drawable.ic_dialog_alert);
			               }
		               });
	}

	private  void showConfirmationAlert() {
		String message = "";

		if (mAction == Action.ENTER) {
			message = mTicket.getTicketOwnerName() + " נכנס/ה בהצלחה לאירוע.";
			if (mState == State.ERALY_ENTRANCE) {
				message += "\n" + "הקצאה לכניסה מוקדמת - " + (mSelectedGroup != null ? mSelectedGroup
						.getName() : "הפקה");
			}
		} else {
			message = mTicket.getTicketOwnerName() + " יצא/ה בהצלחה מהאירוע.";
		}

		AlertDialog.Builder builder = new AlertDialog.Builder(ShowActivity.this);
		builder.setMessage(message)
				.setTitle("אישור")
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						Intent intent = new Intent(ShowActivity.this, MainActivity.class);
						startActivity(intent);
					}
				});
		AlertDialog dialog = builder.create();
		dialog.show();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_show);
		getSupportActionBar().setTitle(getString(R.string.ticket_details));
		bindView();

		mGateCode = getIntent().getStringExtra("event_id");
		if (TextUtils.isEmpty(mGateCode)) {
			mGateCode = AppUtils.getEventId(this);
		}
		mHttpRequestListener = new HttpRequestListener() {
			@Override
			public void onResponse(Response response) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						mProgressBar.setVisibility(View.GONE);
					}
				});
				handleServerResponse(response);
			}
		};

		Ticket ticket = (Ticket) getIntent().getSerializableExtra("ticketDetails");
		if (ticket != null) {

			mTicket = ticket;
			mTicketOrderNumberTextView.setText(String.valueOf(ticket.getInvitationNumber()));
			mTicketNumberTextView.setText(String.valueOf(ticket.getTicketNumber()));
			mTicketOwnerNameTextView.setText(ticket.getTicketOwnerName());
			mTicketTypeTextView.setText(ticket.getTicketType());
			mTicketOwnerIdTextView.setText(ticket.getTicketOwnerId());

			if (mTicket.isProductionEarlyArrival) {
				findViewById(R.id.earlyArrivalProductionTV).setVisibility(View.VISIBLE);
			}

			//decide which button to show (entrance/exit)
			if (ticket.isInsideEvent() == 0) {
				//the user is outside the event
				mAction = Action.ENTER;
			}
			else if (ticket.isInsideEvent() == 1) {
				//the user is inside the event
				mAction = Action.EXIT;
			}
			else {
				Log.e(AppConsts.TAG, "unknown isInsideEvent state. isInsideEvent: " + ticket.isInsideEvent());
			}
			toggleButtonsState();


			//decide if disabled layout should be displayed
			if (ticket.getIsDisabled() == 1) {
				//show disabled parking
				mDisabledLayout.setVisibility(View.VISIBLE);
			}
			else {
				mDisabledLayout.setVisibility(View.GONE);
			}

			//early arrival mode
			if (ticket.getGateStatus() != null && ticket.getGateStatus().equals("early_arrival")) {
				mState = State.ERALY_ENTRANCE;
			}else { //otherwise, this is the real deal
				mState = State.MIDBURN;
			}
		}
	}

	private void toggleButtonsState() {
		if (mAction == Action.EXIT) {
			mEntranceButton.setVisibility(View.GONE);
			mExitButton.setVisibility(View.VISIBLE);
		}
		else {
			mExitButton.setVisibility(View.GONE);
			mEntranceButton.setVisibility(View.VISIBLE);
		}
	}

	private void bindView() {
		mTicketOrderNumberTextView = findViewById(R.id.orderNumberTextView_ShowActivity);
		mTicketNumberTextView = findViewById(R.id.ticketNumberTextView_ShowActivity);
		mTicketOwnerNameTextView = findViewById(R.id.ticketOwnerTextView_ShowActivity);
		mTicketTypeTextView = findViewById(R.id.ticketTypeTextView_ShowActivity);
		mEntranceButton = findViewById(R.id.entranceButton_ShowActivity);
		mExitButton = findViewById(R.id.exitButton_ShowActivity);
		mCancelButton = findViewById(R.id.cancelButton_ShowActivity);
		mProgressBar = findViewById(R.id.progressBar_ShowActivity);
		mTicketOwnerIdTextView = findViewById(R.id.ticketOwnerIdTextView_ShowActivity);
		mDisabledLayout = findViewById(R.id.disabledLayout_ShowActivity);
	}

	@Override
	public void onBackPressed() {
		Intent upIntent = new Intent(this, MainActivity.class);
		NavUtils.navigateUpTo(this, upIntent);
	}
}
