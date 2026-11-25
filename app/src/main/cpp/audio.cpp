#include <jni.h>
#include <fluidsynth.h>
#include <mutex>
#include <memory>
struct Engine { fluid_settings_t* settings=nullptr;fluid_synth_t* synth=nullptr;std::mutex lock;int sf=-1;~Engine(){if(synth)delete_fluid_synth(synth);if(settings)delete_fluid_settings(settings);} };
static Engine* ptr(jlong h){return reinterpret_cast<Engine*>(h);}
extern "C" JNIEXPORT jlong JNICALL Java_id_arus_piano_FluidNative_create(JNIEnv*,jclass,jint rate,jint polyphony){
 auto e=std::make_unique<Engine>(); e->settings=new_fluid_settings();if(!e->settings)return 0;
 fluid_settings_setnum(e->settings,"synth.sample-rate",rate);
 fluid_settings_setnum(e->settings,"synth.gain",.6);
 fluid_settings_setint(e->settings,"synth.polyphony",polyphony);
 fluid_settings_setint(e->settings,"synth.midi-channels",48);
 fluid_settings_setint(e->settings,"synth.threadsafe-api",1);
 fluid_settings_setint(e->settings,"synth.reverb.active",1);
 fluid_settings_setint(e->settings,"synth.chorus.active",0);
 e->synth=new_fluid_synth(e->settings); if(!e->synth)return 0;return reinterpret_cast<jlong>(e.release());
}
extern "C" JNIEXPORT jint JNICALL Java_id_arus_piano_FluidNative_load(JNIEnv* env,jclass,jlong h,jstring path){auto* e=ptr(h);if(!e)return -1;const char* p=env->GetStringUTFChars(path,nullptr);std::lock_guard<std::mutex> g(e->lock);int next=fluid_synth_sfload(e->synth,p,1);env->ReleaseStringUTFChars(path,p);if(next<0)return -1;int old=e->sf;e->sf=next;if(old>=0)fluid_synth_sfunload(e->synth,old,0);return next;}
extern "C" JNIEXPORT void JNICALL Java_id_arus_piano_FluidNative_message(JNIEnv*,jclass,jlong h,jint status,jint a,jint b){auto* e=ptr(h);if(!e)return;std::lock_guard<std::mutex> g(e->lock);int ch=status&63,k=status&0xff00; // private status encodes kind in the high byte, channel in low byte
 if(k==0x9000&&b>0)fluid_synth_noteon(e->synth,ch,a,b);else if(k==0x8000||k==0x9000)fluid_synth_noteoff(e->synth,ch,a);
 else if(k==0xb000)fluid_synth_cc(e->synth,ch,a,b);else if(k==0xc000)fluid_synth_program_change(e->synth,ch,a);
 else if(k==0xe000)fluid_synth_pitch_bend(e->synth,ch,a|(b<<7));
}
extern "C" JNIEXPORT void JNICALL Java_id_arus_piano_FluidNative_render(JNIEnv* env,jclass,jlong h,jshortArray pcm,jint frames){auto* e=ptr(h);if(!e||frames<=0||env->GetArrayLength(pcm)<frames*2)return;jshort* out=env->GetShortArrayElements(pcm,nullptr);{std::lock_guard<std::mutex> g(e->lock);fluid_synth_write_s16(e->synth,frames,out,0,2,out,1,2);}env->ReleaseShortArrayElements(pcm,out,0);}
extern "C" JNIEXPORT void JNICALL Java_id_arus_piano_FluidNative_configure(JNIEnv*,jclass,jlong h,jint poly,jboolean reverb){auto* e=ptr(h);if(!e)return;std::lock_guard<std::mutex> g(e->lock);fluid_synth_set_polyphony(e->synth,poly);fluid_synth_reverb_on(e->synth,-1,reverb);}
extern "C" JNIEXPORT void JNICALL Java_id_arus_piano_FluidNative_panic(JNIEnv*,jclass,jlong h){auto* e=ptr(h);if(!e)return;std::lock_guard<std::mutex> g(e->lock);fluid_synth_system_reset(e->synth);for(int c=0;c<48;c++)fluid_synth_all_sounds_off(e->synth,c);}
extern "C" JNIEXPORT void JNICALL Java_id_arus_piano_FluidNative_destroy(JNIEnv*,jclass,jlong h){delete ptr(h);}
