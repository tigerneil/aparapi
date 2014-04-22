package hsailtest;

import com.amd.aparapi.Device;
import org.junit.Test;

import static com.amd.aparapi.HSA.barrier;
import static com.amd.aparapi.HSA.getCurrentWorkGroupSize;
import static com.amd.aparapi.HSA.getWorkGroupId;
import static com.amd.aparapi.HSA.getWorkItemId;
import static com.amd.aparapi.HSA.localIntX1;
import static org.junit.Assert.assertTrue;

public class ScanReductionMatchedStringsJUnit{

   int add(int lhs, int rhs){
      return (lhs+rhs);
   }

   void scan(int[] arr, int interval){
      Device.hsa().forEach(arr.length/interval, id -> {
         int[] local = localIntX1(); // we need a local int buffer the same width as the group
         int lid = getWorkItemId();
         int lsize = getCurrentWorkGroupSize();
         local[lid] = arr[((id+1)*interval)-1];
         barrier();
         for (int step = 2; step<=lsize; step *= 2){
            if ((lid+1)%step == 0){
               int stride = step/2;
               local[lid] += local[lid-stride];
            }
            barrier();
         }

         for (int step = lsize/2; step>1; step /= 2){
            if (((lid+1)<lsize) && (((lid+1)%step) == 0)){
               int stride = step/2;
               local[lid+stride] = local[lid]+local[lid+stride];
            }
            barrier();
         }
         arr[((id+1)*interval)-1] = local[lid];
         barrier();
      });
   }



//  4    2    3    2    6    1    2    3
//   \   |     \   |     \   |     \   |
//    \  |      \  |      \  |      \  |
//     \ |       \ |       \ |       \ |
//      \|        \|        \|        \|
//       +         +         +         +
//  4    6    3    5    6    7    2    5
//        \        |          \        |
//         \       |           \       |
//          \      |            \      |
//           \     |             \     |
//            \    |              \    |
//             \   |               \   |
//              \  |                \  |
//               \ |                 \ |
//                \|                  \|
//                 +                   +
//  4    6    3   11    6    7    2   12
//                  \                  |
//                   \                 |
//                    \                |
//                     \               |
//                      \              |
//                       \             |
//                        \            |
//                         \           |
//                          \          |
//                           \         |
//                            \        |
//                             \       |
//                              \      |
//                               \     |
//                                \    |
//                                 \   |
//                                  \  |
//                                   \ |
//                                    \|
//                                     +
//  4    6    3   11    6    7    2   23
//                  \        |
//                   \       |
//                    \      |
//                     \     |
//                      \    |
//                       \   |
//                        \  |
//                         \ |
//                          \|
//                           +
//  4    6    3   11    6   18    2   23
//        \   |     \   |     \   |
//         \  |      \  |      \  |
//          \ |       \ |       \ |
//           \|        \|        \|
//            +         +         +
//  4    6   10   11   17   18   20   23

   void fixup(int[] arr){
      Device.hsa().forEach(arr.length, id -> {
         int[] local = localIntX1();
         int lid = getWorkItemId();
         int lsize = getCurrentWorkGroupSize();
         local[lid] = arr[id];
         barrier();

         if (id>=lsize && lid<lsize-1){
            int partial = arr[(getWorkGroupId()*lsize)-1];
            local[lid] = local[lid]+partial;
         }
         barrier();
         arr[id] = local[lid];
         barrier();
      });
   }

   void filter(String[] strings, int[] arr){
      Device.hsa().forEach(arr.length, id -> {
         String s = strings[id];
         if (s.length()>2 && s.charAt(2)=='3'){
            arr[id]=1;
         }else{
            arr[id]=0;
         }

      });
   }

   void map(String[] instrings, int[] arr, String[] outStrings){
      Device.hsa().forEach(arr.length, id -> {
         if (arr[id]>arr[id-1]){
            outStrings[arr[id]]=instrings[id];
         }

      });
   }

   @Test
   public void test(){
      final int len = 65536; // must be a multiple of 256
      String[] instrings = new String[len];

      int[] in = new int[len];

      Device.jtp().forEach(len, id -> instrings[id] = ""+id);


      filter(instrings, in);
      int[] inCopy = JunitHelper.copy(in);
      scan(in, 1);
      scan(in, 256);
      fixup(in);
      int outCount = in[len-1];
      System.out.println("number of strings="+outCount);
      String[] outStrings = new String[outCount];
      map(instrings, in, outStrings);

      for (String s:outStrings){
         System.out.println(s);
      }

      int hsaSum = in[len-1];
      System.out.println("hsaSum "+hsaSum);

      System.out.print("rule");
      for (int i = 0; i<1024; i++){
         if ((i%256) == 0){
            System.out.print(" V  ");
         }else{
            System.out.print(" .  ");
         }
      }

      System.out.println();
      JunitHelper.dump("orig", inCopy, "%3d", 1024);
      JunitHelper.dump(" hsa", in, "%3d", 1024);

      int[] out = new int[len];
      out[0] = inCopy[0];
      for (int i = 1; i<len; i++){
         out[i] = out[i-1]+inCopy[i];
      }
      int sum = out[len-1]+inCopy[len-1];
      System.out.println("sum "+sum);

      JunitHelper.dump(" seq", out, "%3d", 1024);

      assertTrue("HSA equals JTP results", JunitHelper.compare(out, in));

   }
}
