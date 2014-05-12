package com.ne0fhyklabs.freeflight.ui.controls

import android.preference.DialogPreference
import android.content.Context
import android.util.AttributeSet
import kotlin.properties.Delegates
import com.google.android.glass.touchpad.GestureDetector
import com.google.android.glass.touchpad.Gesture
import android.media.AudioManager
import com.google.glass.widget.SliderView
import android.widget.TextView
import android.view.SoundEffectConstants
import android.view.View
import com.ne0fhyklabs.freeflight.R
import com.ne0fhyklabs.freeflight.utils.GlassUtils
import android.view.InputDevice
import android.content.res.TypedArray
import android.util.Log
import android.preference.Preference
import android.view.LayoutInflater
import android.app.DialogFragment
import android.os.Bundle
import android.view.ViewGroup
import android.app.FragmentManager
import android.preference.PreferenceManager
import android.view.WindowManager
import com.ne0fhyklabs.freeflight.fragments.GlassPreferenceFragment

/**
 * Created by fhuya on 5/9/14.
 */
class GlassSliderPreference(val context: Context, attrs: AttributeSet)
: Preference(context, attrs) {

    class object {
        private val TAG = javaClass<GlassSliderPreference>().getSimpleName()
        private val ANDROIDNS: String = "http://schemas.android.com/apk/res/android"
        private val APPLICATIONNS: String = "http://robobunny.com"
        private val DEFAULT_VALUE: Int = 50
    }

    private val mMaxValue = attrs.getAttributeIntValue(ANDROIDNS, "max", 100)
    private val mMinValue = attrs.getAttributeIntValue(APPLICATIONNS, "min", 0)
    private var mCurrentValue = 0

    private val mUnit = getAttributeStringValue(attrs, APPLICATIONNS, "unit", "")

    private fun getAttributeStringValue(attrs: AttributeSet, namespace: String, name: String,
                                        defaultValue: String): String {
        val value = attrs.getAttributeValue(namespace, name)
        return value ?: defaultValue
    }

    protected override fun onGetDefaultValue(ta: TypedArray, index: Int): Any = ta.getInt(index, DEFAULT_VALUE)

    public fun setValue(value: Int) {
        mCurrentValue = value
        persistInt(value)
    }

    override protected fun onSetInitialValue(restoreValue: Boolean, defaultValue: Any) {
        if (restoreValue) {
            mCurrentValue = getPersistedInt(mCurrentValue)
        } else {
            val temp = try {
                defaultValue as Int
            } catch(e: Exception) {
                Log.e(TAG, "Invalid default value $defaultValue")
                0
            }
            mCurrentValue = temp
            persistInt(temp)
        }
    }

    fun persistIntAndNotify(value: Int): Boolean {
        val retvalue = super.persistInt(value)
        callChangeListener(value)
        return retvalue
    }

    fun launchSliderDialog(fm: FragmentManager){
        val sliderDialog = GlassSliderDialog()
        sliderDialog.show(fm, "Slider Preference dialog")
    }

    inner class GlassSliderDialog : DialogFragment() {

        private val mStartValue = mCurrentValue

        private var mSliderBar: SliderView? = null
        private var mSliderValue: TextView? = null

        private val mGlassDetector: GestureDetector by Delegates.lazy {
            val context = getActivity()?.getApplicationContext()
            val audioMan = context?.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val gestureDetector = GestureDetector(context)

            gestureDetector.setBaseListener {
                when(it) {
                    Gesture.TAP -> {
                        audioMan.playSoundEffect(SoundEffectConstants.CLICK)

                        //Save the current value
                        if(mCurrentValue != mStartValue){
                            persistIntAndNotify(mCurrentValue)
                        }

                        //Close the preference dialog
                        getDialog()?.dismiss()
                        true
                    }

                    else -> false
                }
            }

            gestureDetector.setScrollListener { displacement, delta, velocity ->
                if (mSliderBar != null) {
                    val range =  mMaxValue - mMinValue
                    val update = (delta * (range / 600f)).toFloat()

                    mCurrentValue += update.toInt()
                    if (mCurrentValue > mMaxValue) {
                        mCurrentValue = mMaxValue
                    } else if (mCurrentValue < mMinValue) {
                        mCurrentValue = mMinValue
                    }

                    setSliderProgress()

                    mSliderValue?.setText("$mCurrentValue $mUnit")
                }

                true
            }

            gestureDetector
        }

        private fun setSliderProgress() {
            val updateFraction = (mCurrentValue - mMinValue).toFloat() / (mMaxValue - mMinValue)
                    .toFloat()

            mSliderBar?.setManualProgress(updateFraction)
        }

        override fun onCreate(savedInstanceState: Bundle?){
            super.onCreate(savedInstanceState)
            getActivity()?.getWindow()?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                                  savedInstanceState: Bundle?): View? {
            val view = inflater.inflate(R.layout.dialog_preference_glass_slider, container, false)

            mSliderValue = view?.findViewById(R.id.glass_slider_value) as TextView
            mSliderValue?.setText("$mCurrentValue $mUnit")

            mSliderBar = view?.findViewById(R.id.glass_slider_bar) as SliderView

            return view
        }

        override fun onStart(){
            super.onStart()
            GlassPreferenceFragment.updateWindowCallback(getDialog()?.getWindow(), mGlassDetector)
        }

        override fun onStop(){
            super.onStop()
            GlassPreferenceFragment.restoreWindowCallback(getDialog()?.getWindow())
        }

        override fun onResume(){
            super.onResume()
            setSliderProgress()
        }

    }
}