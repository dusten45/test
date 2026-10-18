package android.content;

public interface DialogInterface {
    int BUTTON_POSITIVE = -1, BUTTON_NEGATIVE = -2, BUTTON_NEUTRAL = -3;
    void cancel();
    void dismiss();
    interface OnClickListener { void onClick(DialogInterface dialog, int which); }
    interface OnCancelListener { void onCancel(DialogInterface dialog); }
    interface OnDismissListener { void onDismiss(DialogInterface dialog); }
}
