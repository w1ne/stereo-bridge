package app.stereobridge.handoff;

/** Experimental alignment of 10 ms energy envelopes, never a timestamp guarantee. */
final class SyncEstimator {
  static final class Result {
    final int offsetBins;
    final double correlation;
    final boolean confident;
    Result(int offsetBins, double correlation, boolean confident) {
      this.offsetBins = offsetBins; this.correlation = correlation; this.confident = confident;
    }
  }

  static Result align(float[] camera, float[] microphone) {
    if (camera == null || microphone == null || camera.length < 300 || microphone.length < camera.length)
      return new Result(0, 0, false);
    if (camera.length > 12000 || microphone.length > 30000) return new Result(0, 0, false);
    camera = features(camera);
    microphone = features(microphone);
    int count = camera.length;
    int maximum = Math.min(microphone.length - count, 18000);
    Peak peak = search(camera,0,microphone,count,maximum,-1);
    double best=peak.score; int offset=peak.offset;
    double alternative=search(camera,0,microphone,count,maximum,offset).score;
    boolean confident = best >= .60 && best - alternative >= .15;
    // Independent windows must agree. A good average cannot conceal drift or a
    // mismatched portion of a take. Short quiet/ambiguous takes fail closed.
    int window = Math.min(1000, Math.max(300, count/3));
    for (int start : new int[]{0, (count-window)/2, count-window}) {
      Peak local=search(camera,start,microphone,window,maximum,-1);
      confident &= local.score >= .55 && Math.abs(local.offset-offset) <= 3;
    }
    return new Result(offset, best, confident);
  }

  private static final class Peak {
    final int offset; final double score;
    Peak(int offset,double score) { this.offset=offset;this.score=score; }
  }

  private static Peak search(float[] camera,int start,float[] microphone,int count,int maximum,int avoid) {
    double[] scores=new double[8];int[] seeds=new int[8];java.util.Arrays.fill(scores,-2);
    for(int candidate=0;candidate<=maximum;candidate+=5) {
      if(avoid>=0 && Math.abs(candidate-avoid)<20)continue;
      double score=correlation(camera,start,microphone,candidate+start,count,5);
      for(int slot=0;slot<scores.length;slot++) if(score>scores[slot]) {
        for(int move=scores.length-1;move>slot;move--) { scores[move]=scores[move-1];seeds[move]=seeds[move-1]; }
        scores[slot]=score;seeds[slot]=candidate;break;
      }
    }
    double best=-2;int offset=0;
    for(int slot=0;slot<scores.length;slot++) {
      if(scores[slot]<=-2)continue;
      for(int candidate=Math.max(0,seeds[slot]-5);candidate<=Math.min(maximum,seeds[slot]+5);candidate++) {
        if(avoid>=0 && Math.abs(candidate-avoid)<20)continue;
        double score=correlation(camera,start,microphone,candidate+start,count,1);
        if(score>best) { best=score;offset=candidate; }
      }
    }
    return new Peak(offset,best);
  }

  private static float[] features(float[] input) {
    float[] smooth = new float[input.length], result = new float[input.length];
    for (int i=0; i<input.length; i++) {
      double sum=0; int first=Math.max(0,i-2), last=Math.min(input.length,i+3);
      for (int j=first;j<last;j++) sum+=input[j];
      smooth[i]=(float)Math.log1p(sum/(last-first));
    }
    double[] prefix = new double[input.length+1];
    for (int i=0;i<input.length;i++) prefix[i+1]=prefix[i]+smooth[i];
    for (int i=0;i<input.length;i++) {
      int first=Math.max(0,i-50),last=Math.min(input.length,i+51);
      result[i]=smooth[i]-(float)((prefix[last]-prefix[first])/(last-first));
    }
    return result;
  }

  private static double correlation(float[] camera, int cameraStart, float[] microphone, int offset, int count,int stride) {
    double a = 0, b = 0, aa = 0, bb = 0, ab = 0;
    int n=0;
    for (int i = 0; i < count; i+=stride,n++) {
      double x = camera[cameraStart+i], y = microphone[offset + i];
      if (!Double.isFinite(x) || !Double.isFinite(y)) return -1;
      a += x; b += y; aa += x*x; bb += y*y; ab += x*y;
    }
    double varianceA = aa - a*a/n, varianceB = bb - b*b/n;
    if (varianceA <= 1e-12 || varianceB <= 1e-12) return -1;
    return (ab - a*b/n) / Math.sqrt(varianceA*varianceB);
  }
}
