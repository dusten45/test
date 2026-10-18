package android.opengl;

import javax.microedition.khronos.opengles.GL10;

public final class GLU {
    private GLU() {}
    public static void gluPerspective(GL10 gl, float fovy, float aspect, float near, float far) {
        float top = near * (float)Math.tan(Math.toRadians(fovy)*0.5);
        gl.glFrustumf(-top*aspect, top*aspect, -top, top, near, far);
    }
    public static void gluLookAt(GL10 gl, float eyeX,float eyeY,float eyeZ,float centerX,float centerY,float centerZ,float upX,float upY,float upZ) {
        float fx=centerX-eyeX,fy=centerY-eyeY,fz=centerZ-eyeZ;
        float r=1f/(float)Math.sqrt(fx*fx+fy*fy+fz*fz); fx*=r;fy*=r;fz*=r;
        float sx=fy*upZ-fz*upY,sy=fz*upX-fx*upZ,sz=fx*upY-fy*upX;
        r=1f/(float)Math.sqrt(sx*sx+sy*sy+sz*sz);sx*=r;sy*=r;sz*=r;
        float ux=sy*fz-sz*fy,uy=sz*fx-sx*fz,uz=sx*fy-sy*fx;
        gl.glMultMatrixf(new float[]{sx,ux,-fx,0,sy,uy,-fy,0,sz,uz,-fz,0,0,0,0,1},0);
        gl.glTranslatef(-eyeX,-eyeY,-eyeZ);
    }
    public static String gluErrorString(int error) { return "OpenGL error 0x"+Integer.toHexString(error); }
}
