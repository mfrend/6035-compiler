#include <stdio.h>
#include <stdlib.h>
#include <time.h>



// the derby program: levels adjustment followed by unsharp mask by gaussian smoothing
  
  int image[2300000];
  int unsharpMask [750000];

  int unsharpKernel[9];
  int kernel_sum  = 0;
  int cols  = 0, rows  = 0;
  
  void read_file()
  {
    int r = 0, c = 0;

    ppm_open_for_read( "input.ppm");
    cols =  ppm_get_cols();
    rows =  ppm_get_rows();
     for (r  =  0;  r  <  rows ;  r ++){
       for (c  =  0;  c  <  cols ;  c ++){
	image[((r * 2193) + (c * 3)) + 0] =  ppm_get_next_pixel_color();
	image[((r * 2193) + (c * 3)) + 1] =  ppm_get_next_pixel_color();
	image[((r * 2193) + (c * 3)) + 2] =  ppm_get_next_pixel_color();
      }
    }

     ppm_close();
  }

  void write_file()
  {
    int r = 0, c = 0;

     ppm_open_for_write( "output.ppm", cols, rows);
     for (r  =  0;  r  <  rows ;  r ++){
       for (c  =  0;  c  <  cols ;  c ++){
	 ppm_write_next_pixel( image[((r * 2193) + (c * 3))], image[((r * 2193) + (c * 3)) + 1], image[((r * 2193) + (c * 3)) + 2]);
      }
    }

     ppm_close();
  }

  void convert2HSV()
  {
    int row = 0, col = 0;
     for (row  =  0;  row  <  rows ;  row ++){
       for (col  =  0;  col  <  cols ;  col ++){
	int min = 0, max = 0;
	int delta = 0;
	int h = 0, s = 0, v = 0;

	if (image[((row * 2193) + (col * 3)) + 0] > image[((row * 2193) + (col * 3)) + 1]) {
	  max = image[((row * 2193) + (col * 3)) + 0];
	  min = image[((row * 2193) + (col * 3)) + 1];
	} else {
	  max = image[((row * 2193) + (col * 3)) + 1];
	  min = image[((row * 2193) + (col * 3)) + 0];
	}

	if (max < image[((row * 2193) + (col * 3)) + 2]) {
	  max = image[((row * 2193) + (col * 3)) + 2];
	} else { 
	  if (min > image[((row * 2193) + (col * 3)) + 2]) {
	    min = image[((row * 2193) + (col * 3)) + 2];
	  }
	}

	delta = max - min;
	v = 4 * max;
	if (max == 0) {
	  s = 0;
	} else {
	  s = 1024 * delta / max;
	}

	if (delta == 0) {
	  h = -1;
	} else 
	{
	  if (max == image[((row * 2193) + (col * 3)) + 0] && image[((row * 2193) + (col * 3)) + 1] >= image[((row * 2193) + (col * 3)) + 2]) {
	    h = 60 * (image[((row * 2193) + (col * 3)) + 1] - image[((row * 2193) + (col * 3)) + 2]) / delta;
	  } else {
	    if (max == image[((row * 2193) + (col * 3)) + 0] && image[((row * 2193) + (col * 3)) + 1] < image[((row * 2193) + (col * 3)) + 2]) {
	      h = 360 + 60 * (image[((row * 2193) + (col * 3)) + 1] - image[((row * 2193) + (col * 3)) + 2]) / delta;
	    } else {
	      if (max == image[((row * 2193) + (col * 3)) + 1]) {
		h = 120 + 60 * (image[((row * 2193) + (col * 3)) + 2] - image[((row * 2193) + (col * 3)) + 0]) / delta;
	      } else {
		h = 240 + 60 * (image[((row * 2193) + (col * 3)) + 0] - image[((row * 2193) + (col * 3)) + 1]) / delta;
	      }
	    }
	  }
	}

	image[((row * 2193) + (col * 3)) + 0] = h;
	image[((row * 2193) + (col * 3)) + 1] = s;
	image[((row * 2193) + (col * 3)) + 2] = v;
      }
    }
  }

  void convert2RGB()
  {
    int row = 0, col = 0;

     for (row  =  0;  row  <  rows ;  row ++){
       for (col  =  0;  col  <  cols ;  col ++){
	int r = 0, g = 0, b = 0;
	int v = 0;
	int j = 0;
	int f = 0, p = 0, q = 0, t = 0;

	if (image[((row * 2193) + (col * 3)) + 1] == 0) {
	  r = image[((row * 2193) + (col * 3)) + 2] / 4;
	  g = image[((row * 2193) + (col * 3)) + 2] / 4;
	  b = image[((row * 2193) + (col * 3)) + 2] / 4;
	} else {
	  j = (image[((row * 2193) + (col * 3)) + 0] / 60) % 6;
	  f = image[((row * 2193) + (col * 3)) + 0] % 60;
	  p = image[((row * 2193) + (col * 3)) + 2] * (1024 - image[((row * 2193) + (col * 3)) + 1]) / (1024 * 4);
	  q = image[((row * 2193) + (col * 3)) + 2] * (1024 * 60 - image[((row * 2193) + (col * 3)) + 1] * f) / (1024 * 60 * 4);
	  t = image[((row * 2193) + (col * 3)) + 2] * (1024 * 60 - image[((row * 2193) + (col * 3)) + 1] * (60 - f)) / (1024 * 60 * 4);
	  v = image[((row * 2193) + (col * 3)) + 2] / 4;

	  if (j==0)
	  {
	    r = v;
	    g = t;
	    b = p;
	  }
	  if (j==1)
	  {
	    r = q;
	    g = v;
	    b = p;
	  }
	  if (j==2)
	  {
	    r = p;
	    g = v;
	    b = t;
	  }
	  if (j==3)
	  {
	    r = p;
	    g = q;
	    b = v;
	  }
	  if (j==4)
	  {
	    r = t;
	    g = p;
	    b = v;
	  }
	  if (j==5)
	  {
	    r = v;
	    g = p;
	    b = q;
	  }
	}

	image[((row * 2193) + (col * 3)) + 0] = r;
	image[((row * 2193) + (col * 3)) + 1] = g;
	image[((row * 2193) + (col * 3)) + 2] = b;
      }
    }
  }


  void createKernel() {
    int center = 0, i = 0;
    
    unsharpKernel[0] = 4433;
    unsharpKernel[1] = 54006;
    unsharpKernel[2] = 242036;
    unsharpKernel[3] = 399050;
    unsharpKernel[4] = 242036;
    unsharpKernel[5] = 54006;
    unsharpKernel[6] = 4433;

    center = 3;
    kernel_sum = 0;

     for (i  =  0;  i  <  center * 2 + 1 ;  i ++){
      kernel_sum = kernel_sum + unsharpKernel[i];

    }
  }

  void createUnsharpMaskH()
  {
    int center = 0;
    int r = 0, c = 0;

    center = 3;
    
    // convolve in x-direction
     for (r  =  0;  r  <  rows ;  r ++){
       for (c  =  0;  c  <  center ;  c ++){
	unsharpMask[r * 731 + c] = image[((r * 2193) + (c * 3))];
      }

       for (c  =  center;  c  <  cols - center ;  c ++){
	unsharpMask[r * 731 + c] = 0;
		
	unsharpMask[r * 731 + c] += (image[(r * 2193) + (3 * c) - 9] * unsharpKernel[0]);
	unsharpMask[r * 731 + c] += (image[(r * 2193) + (3 * c) - 6] * unsharpKernel[1]);
	unsharpMask[r * 731 + c] += (image[(r * 2193) + (3 * c) - 3] * unsharpKernel[2]);
	unsharpMask[r * 731 + c] += (image[(r * 2193) + (3 * c)] * unsharpKernel[3]);
	unsharpMask[r * 731 + c] += (image[(r * 2193) + (3 * c) + 3] * unsharpKernel[4]);
	unsharpMask[r * 731 + c] += (image[(r * 2193) + (3 * c) + 6] * unsharpKernel[5]);
	unsharpMask[r * 731 + c] += (image[(r * 2193) + (3 * c) + 9] * unsharpKernel[6]);

	unsharpMask[r * 731 + c] = unsharpMask[r * 731 + c] / kernel_sum;
      }

       for (c  =  cols - center;  c  <  cols ;  c ++){
	unsharpMask[r * 731 + c] = image[((r * 2193) + (c * 3))];
      }
    }

    unsharpKernel[center] = unsharpKernel[center] - kernel_sum;

    // convolve in y-direction    
     for (c  =  0;  c  <  cols ;  c ++){
      int m1 = 0, m2 = 0, m3 = 0;
      
      m1 = unsharpMask[c];
      m2 = unsharpMask[c + 731];
      m3 = unsharpMask[c + 1462];

       for (r  =  0;  r  <  center ;  r ++){
	unsharpMask[r * 731 + c] = 0;
      }
      
       for (r  =  center;  r  <  rows - center ;  r ++){
	int dot = 0;
	
	dot = 0;

	dot += unsharpMask[r * 731 + c] + (m1 * unsharpKernel[0]);
	dot += unsharpMask[r * 731 + c] + (m2 * unsharpKernel[1]);
	dot += unsharpMask[r * 731 + c] + (m3 * unsharpKernel[2]);
	dot += unsharpMask[r * 731 + c] + (unsharpMask[(731 * r) + c] * unsharpKernel[3]);
	dot += unsharpMask[r * 731 + c] + (unsharpMask[(731 * r) + c + 731] * unsharpKernel[4]);
	dot += unsharpMask[r * 731 + c] + (unsharpMask[(731 * r) + c + 1462] * unsharpKernel[5]);
	dot += unsharpMask[r * 731 + c] + (unsharpMask[(731 * r) + c + 2193] * unsharpKernel[6]);

	m1 = m2;
	m2 = m3;
	m3 = unsharpMask[r * 731 + c];

	unsharpMask[r * 731 + c] = dot / kernel_sum;
      }

       for (r  =  rows - center;  r  <  rows ;  r ++){
	unsharpMask[r * 731 + c] = 0;
      }
    }
    
    unsharpKernel[center] = unsharpKernel[center] + kernel_sum;
  }


  void createUnsharpMaskS()
  {
    int center = 0;
    int r = 0, c = 0;

    center = 3;
    
    // convolve in x-direction
     for (r  =  0;  r  <  rows ;  r ++){
       for (c  =  0;  c  <  center ;  c ++){
	unsharpMask[r * 731 + c] = image[((r * 2193) + (c * 3)) + 1];
      }

       for (c  =  center;  c  <  cols - center ;  c ++){
	
	unsharpMask[r * 731 + c] = 0;
		
	unsharpMask[r * 731 + c] += (image[(r * 2193) + (3 * c) - 8] * unsharpKernel[0]);
	unsharpMask[r * 731 + c] += (image[(r * 2193) + (3 * c) - 5] * unsharpKernel[1]);
	unsharpMask[r * 731 + c] += (image[(r * 2193) + (3 * c) - 2] * unsharpKernel[2]);
	unsharpMask[r * 731 + c] += (image[(r * 2193) + (3 * c) + 1] * unsharpKernel[3]);
	unsharpMask[r * 731 + c] += (image[(r * 2193) + (3 * c) + 4] * unsharpKernel[4]);
	unsharpMask[r * 731 + c] += (image[(r * 2193) + (3 * c) + 7] * unsharpKernel[5]);
	unsharpMask[r * 731 + c] += (image[(r * 2193) + (3 * c) + 10] * unsharpKernel[6]);

	unsharpMask[r * 731 + c] = unsharpMask[r * 731 + c] / kernel_sum;
      }

       for (c  =  cols - center;  c  <  cols ;  c ++){
	unsharpMask[r * 731 + c] = image[((r * 2193) + (c * 3)) + 1];
      }
    }

    unsharpKernel[center] = unsharpKernel[center] - kernel_sum;

    // convolve in y-direction    
     for (c  =  0;  c  <  cols ;  c ++){
      int m1 = 0, m2 = 0, m3 = 0;

      m1 = unsharpMask[c];
      m2 = unsharpMask[c + 731];
      m3 = unsharpMask[c + 1462];
      
       for (r  =  0;  r  <  center ;  r ++){
	unsharpMask[r * 731 + c] = 0;
      }

       for (r  =  center;  r  <  rows - center ;  r ++){
	int dot = 0;
	dot = 0;
	
	dot += unsharpMask[r * 731 + c] + (m1 * unsharpKernel[0]);
	dot += unsharpMask[r * 731 + c] + (m2 * unsharpKernel[1]);
	dot += unsharpMask[r * 731 + c] + (m3 * unsharpKernel[2]);
	dot += unsharpMask[r * 731 + c] + (unsharpMask[(731 * r) + c] * unsharpKernel[3]);
	dot += unsharpMask[r * 731 + c] + (unsharpMask[(731 * r) + c + 731] * unsharpKernel[4]);
	dot += unsharpMask[r * 731 + c] + (unsharpMask[(731 * r) + c + 1462] * unsharpKernel[5]);
	dot += unsharpMask[r * 731 + c] + (unsharpMask[(731 * r) + c + 2193] * unsharpKernel[6]);

	m1 = m2;
	m2 = m3;
	m3 = unsharpMask[r * 731 + c];

	unsharpMask[r * 731 + c] = dot / kernel_sum;
      }

       for (r  =  rows - center;  r  <  rows ;  r ++){
	unsharpMask[r * 731 + c] = 0;
      }
    }
    
    unsharpKernel[center] = unsharpKernel[center] + kernel_sum;
  }

  void createUnsharpMaskV()
  {
    int center = 0;
    int r = 0, c = 0;

    center = 3;
    
    // convolve in x-direction
     for (r  =  0;  r  <  rows ;  r ++){
      
       for (c  =  0;  c  <  center ;  c ++){
	unsharpMask[r * 731 + c] = image[((r * 2193) + (c * 3)) + 2];
      }

       for (c  =  center;  c  <  cols - center ;  c ++){	
	unsharpMask[r * 731 + c] = 0;
	
	unsharpMask[r * 731 + c] += (image[(r * 2193) + (3 * c) - 7] * unsharpKernel[0]);
	unsharpMask[r * 731 + c] += (image[(r * 2193) + (3 * c) - 4] * unsharpKernel[1]);
	unsharpMask[r * 731 + c] += (image[(r * 2193) + (3 * c) - 1] * unsharpKernel[2]);
	unsharpMask[r * 731 + c] += (image[(r * 2193) + (3 * c) + 2] * unsharpKernel[3]);
	unsharpMask[r * 731 + c] += (image[(r * 2193) + (3 * c) + 5] * unsharpKernel[4]);
	unsharpMask[r * 731 + c] += (image[(r * 2193) + (3 * c) + 8] * unsharpKernel[5]);
	unsharpMask[r * 731 + c] += (image[(r * 2193) + (3 * c) + 11] * unsharpKernel[6]);

	unsharpMask[r * 731 + c] = unsharpMask[r * 731 + c] / kernel_sum;
      }

       for (c  =  cols - center;  c  <  cols ;  c ++){
	unsharpMask[r * 731 + c] = image[((r * 2193) + (c * 3)) + 2];
      }
    }

    unsharpKernel[center] = unsharpKernel[center] - kernel_sum;
    
    // convolve in y-direction    
     for (c  =  0;  c  <  cols ;  c ++){
      int m1 = 0, m2 = 0, m3 = 0;
     
      m1 = unsharpMask[c];
      m2 = unsharpMask[c + 731];
      m3 = unsharpMask[c + 1462];

       for (r  =  0;  r  <  center ;  r ++){
	unsharpMask[r * 731 + c] = 0;
      }
      
       for (r  =  center;  r  <  rows - center ;  r ++){
	int dot = 0;
	
	dot = 0;

	dot += unsharpMask[r * 731 + c] + (m1 * unsharpKernel[0]);
	dot += unsharpMask[r * 731 + c] + (m2 * unsharpKernel[1]);
	dot += unsharpMask[r * 731 + c] + (m3 * unsharpKernel[2]);
	dot += unsharpMask[r * 731 + c] + (unsharpMask[(731 * r) + c] * unsharpKernel[3]);
	dot += unsharpMask[r * 731 + c] + (unsharpMask[(731 * r) + c + 731] * unsharpKernel[4]);
	dot += unsharpMask[r * 731 + c] + (unsharpMask[(731 * r) + c + 1462] * unsharpKernel[5]);
	dot += unsharpMask[r * 731 + c] + (unsharpMask[(731 * r) + c + 2193] * unsharpKernel[6]);

	m1 = m2;
	m2 = m3;
	m3 = unsharpMask[r * 731 + c];

	unsharpMask[r * 731 + c] = dot / kernel_sum;
      }

       for (r  =  rows - center;  r  <  rows ;  r ++){
	unsharpMask[r * 731 + c] = 0;
      }
    }

    unsharpKernel[center] = unsharpKernel[center] + kernel_sum;
  }


  void sharpenH(int amount, int channelOne)
  {
    int c = 0, r = 0;

     for (c  =  0;  c  <  cols ;  c ++){
       for (r  =  0;  r  <  rows ;  r ++){
	image[((r * 2193) + (c * 3))] =
        image[((r * 2193) + (c * 3))] * (channelOne + amount * unsharpMask[r * 731 + c]) / channelOne;
	if (image[((r * 2193) + (c * 3))] >= channelOne)
	{
	  image[((r * 2193) + (c * 3))] = channelOne - 1;
	}
      }
    }
  }


  void sharpenS(int amount, int channelOne)
  {
    int c = 0, r = 0;

     for (c  =  0;  c  <  cols ;  c ++){
       for (r  =  0;  r  <  rows ;  r ++){
	image[((r * 2193) + (c * 3)) + 1] =
        image[((r * 2193) + (c * 3)) + 1] * (channelOne + amount * unsharpMask[r * 731 + c]) / channelOne;
	if (image[((r * 2193) + (c * 3)) + 1] >= channelOne)
	{
	  image[((r * 2193) + (c * 3)) + 1] = channelOne - 1;
	}
      }
    }
  }


  void sharpenV(int amount, int channelOne)
  {
    int c = 0, r = 0;

     for (c  =  0;  c  <  cols ;  c ++){
       for (r  =  0;  r  <  rows ;  r ++){
	image[((r * 2193) + (c * 3)) + 2] =
        image[((r * 2193) + (c * 3)) + 2] * (channelOne + amount * unsharpMask[r * 731 + c]) / channelOne;
	if (image[((r * 2193) + (c * 3)) + 2] >= channelOne)
	{
	  image[((r * 2193) + (c * 3)) + 2] = channelOne - 1;
	}
      }
    }
  }


  void levels() {
    int c = 0, r = 0;
    int b = 0;
    int w = 0;
    
    b = 10;
    w = 243;

     for (r  =  0;  r  <  rows ;  r ++){
       for (c  =  0;  c  <  cols ;  c ++){

	//r
	image[(r * 2193) + (c * 3)] = ((image[(r * 2193) + (c * 3)] - b) * 255) / (w - b);
	if (image[(r * 2193) + (c * 3)] < 0) {
	  image[(r * 2193) + (c * 3)] = 0;
	} else {
	  if (image[(r * 2193) + (c * 3)] > 255) {
	    image[(r * 2193) + (c * 3)] = 255;
	  }
	}	  
	//g
	image[(r * 2193) + (c * 3) + 1] = ((image[(r * 2193) + (c * 3) + 1] - b) * 255) / (w - b);
	if (image[(r * 2193) + (c * 3) + 1] < 0) {
	  image[(r * 2193) + (c * 3) + 1] = 0;
	} else {
	  if (image[(r * 2193) + (c * 3) + 1] > 255) {
	    image[(r * 2193) + (c * 3) + 1] = 255;
	  }
	}

	//b
	image[(r * 2193) + (c * 3) + 2] = ((image[(r * 2193) + (c * 3) + 2] - b) * 255) / (w - b);
	if (image[(r * 2193) + (c * 3) + 2] < 0) {
	  image[(r * 2193) + (c * 3) + 2] = 0;
	} else {
	  if (image[(r * 2193) + (c * 3) + 2] > 255) {
	    image[(r * 2193) + (c * 3) + 2] = 255;
	  }
	}
      }
    }
  }



  int main() {
    read_file();
  
     start_caliper();

    levels();
  
    convert2HSV();

    createKernel();
  
    createUnsharpMaskH();
    sharpenH(-4, 360);
    createUnsharpMaskS();
    sharpenS(-4, 1024);
    createUnsharpMaskV();
    sharpenV(-4, 1024);
  
    convert2RGB ();
  
     end_caliper();
  
    write_file();
  }

  
