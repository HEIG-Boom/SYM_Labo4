package ch.heigvd.iict.sym_labo4;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.GLSurfaceView;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

import ch.heigvd.iict.sym_labo4.gl.OpenGLRenderer;

/**
 * Activity that creates a 3D compass to display the direction of magnetic north while remaining horizontal.
 * It's based on acceleration and magnetic sensors
 *
 * @author HEIG-VD
 * @author Modified by : Jael Dubey, Loris Gilliand, Mateo Tutic, Luc Wachter
 * @version 1.0
 * @since 2019-12-06
 */
public class CompassActivity extends AppCompatActivity implements SensorEventListener {
    // opengl
    private OpenGLRenderer opglr = null;
    private GLSurfaceView m3DView = null;

    // Sensors
    private SensorManager mSensorManager = null;
    private Sensor mAccelerometer = null;
    private Sensor mMagnetometer = null;
    float[] rotationMatrix = new float[16];
    float[] gravity = new float[3];
    float[] geomagnetic = new float[3];

    /**
     * Called when the activity is starting. Initializes opengl surface view and register to sensors
     *
     * @param savedInstanceState Bundle object containing the activity's previously saved state.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // we need fullscreen
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // we initiate the view
        setContentView(R.layout.activity_compass);

        // we create the renderer
        this.opglr = new OpenGLRenderer(getApplicationContext());

        // link to GUI
        this.m3DView = findViewById(R.id.compass_opengl);

        //init opengl surface view
        this.m3DView.setRenderer(this.opglr);

        // Register to accelerometer and magnetometer sensors' updates
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mMagnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }


    /**
     * Called after onPause, for this activity to start interacting with the user.
     * Registers to sensors' updates
     */
    @Override
    protected void onResume() {
        super.onResume();

        // Register to accelerometer and magnetometer sensors
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mMagnetometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    /**
     * Called when the user no longer actively interacts with the activity, but it is still visible on screen.
     * Disables sensors we don't need to avoid to drain the battery.
     */
    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    /**
     * Called when there is a new sensor event.
     *
     * @param event Represents the sensor event and holds multiple information about the sensor
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        // Check the type of the sensor to know what to do
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, geomagnetic, 0, geomagnetic.length);
            mSensorManager.getRotationMatrix(this.opglr.swapRotMatrix(rotationMatrix), null, gravity, geomagnetic);
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, gravity, 0, gravity.length);
            mSensorManager.getRotationMatrix(this.opglr.swapRotMatrix(rotationMatrix), null, gravity, geomagnetic);
        }
    }

    /**
     * Called when the accuracy of the registered sensor has changed.
     * It's only called when this accuracy value changes.
     *
     * @param sensor   The registered sensor
     * @param accuracy The new accuracy ot thhe registered sensor
     */
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
