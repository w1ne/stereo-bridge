#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include "lc3.h"
JNIEXPORT jlong JNICALL Java_app_stereobridge_mic_NativeLc3_create(JNIEnv* env,jclass type){
 unsigned size=lc3_decoder_size(5000,48000);void* memory=calloc(1,size);if(!memory)return 0;
 lc3_decoder_t decoder=lc3_setup_decoder(5000,48000,48000,memory);if(!decoder){free(memory);return 0;}return (jlong)(uintptr_t)decoder;
}
JNIEXPORT jshortArray JNICALL Java_app_stereobridge_mic_NativeLc3_decodeFrame(JNIEnv* env,jclass type,jlong handle,jbyteArray encoded){
 uint8_t bytes[111];int16_t samples[240];if(!handle||(*env)->GetArrayLength(env,encoded)!=111)return NULL;
 (*env)->GetByteArrayRegion(env,encoded,0,111,(jbyte*)bytes);
 int result=lc3_decode((lc3_decoder_t)(uintptr_t)handle,bytes,111,LC3_PCM_FORMAT_S16,samples,1);
 if(result<0){jclass cls=(*env)->FindClass(env,"java/lang/IllegalArgumentException");(*env)->ThrowNew(env,cls,"LC3 frame rejected");return NULL;}
 jshortArray output=(*env)->NewShortArray(env,240);if(output)(*env)->SetShortArrayRegion(env,output,0,240,samples);return output;
}
JNIEXPORT void JNICALL Java_app_stereobridge_mic_NativeLc3_destroy(JNIEnv* env,jclass type,jlong handle){free((void*)(uintptr_t)handle);}

JNIEXPORT jshortArray JNICALL Java_app_stereobridge_mic_NativeLc3_concealFrame(JNIEnv* env,jclass type,jlong handle){
 int16_t samples[240];if(!handle)return NULL;
 int result=lc3_decode((lc3_decoder_t)(uintptr_t)handle,NULL,111,LC3_PCM_FORMAT_S16,samples,1);
 if(result<0){jclass cls=(*env)->FindClass(env,"java/lang/IllegalStateException");(*env)->ThrowNew(env,cls,"LC3 concealment failed");return NULL;}
 jshortArray output=(*env)->NewShortArray(env,240);if(output)(*env)->SetShortArrayRegion(env,output,0,240,samples);return output;
}
