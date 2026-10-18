package android.app;
import android.content.Context;
import android.content.DialogInterface;
import javax.swing.JOptionPane;
import javax.swing.JTextArea;
/** Desktop presentation; the retained game's own listeners implement behavior. */
public final class AlertDialog implements DialogInterface {
    public void cancel() {}
    public void dismiss() {}
    public static final class Builder {
        private CharSequence title="",message="",positive="OK",negative;
        private DialogInterface.OnClickListener positiveListener,negativeListener;
        private boolean cancelable=true;
        public Builder(Context context) {}
        public Builder setTitle(CharSequence value) { title=value;return this; }
        public Builder setMessage(CharSequence value) { message=value;return this; }
        public Builder setIcon(int ignored) { return this; }
        public Builder setCancelable(boolean value) { cancelable=value;return this; }
        public Builder setPositiveButton(CharSequence text,DialogInterface.OnClickListener callback) { positive=text;positiveListener=callback;return this; }
        public Builder setNegativeButton(CharSequence text,DialogInterface.OnClickListener callback) { negative=text;negativeListener=callback;return this; }
        public AlertDialog show() {
            AlertDialog dialog=new AlertDialog();
            JTextArea body=new JTextArea(message==null?"":message.toString(),0,46);
            body.setEditable(false);body.setLineWrap(true);body.setWrapStyleWord(true);body.setOpaque(false);
            body.setFont(javax.swing.UIManager.getFont("Label.font"));
            Object[] choices=negative==null?new Object[]{positive}:new Object[]{positive,negative};
            int result;
            do {
                result=JOptionPane.showOptionDialog(null,body,title==null?"":title.toString(),JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE,null,choices,choices[choices.length-1]);
            } while(!cancelable&&result==JOptionPane.CLOSED_OPTION);
            if(result==0&&positiveListener!=null)positiveListener.onClick(dialog,-1);
            else if(result==1&&negativeListener!=null)negativeListener.onClick(dialog,-2);
            return dialog;
        }
    }
}
