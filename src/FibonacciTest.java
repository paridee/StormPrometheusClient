
public class FibonacciTest {

    public static long fibonacci(long i) {
	/* F(i) non e` definito per interi i negativi! */
	if (i == 0) return 0;
	else if (i == 1) return 1;
	else return fibonacci(i-1) + fibonacci(i-2);
    }
    
	public static void main(String[] args) {
		for(int i=0;i<50;i++){
			long total	=	0;
			for(int j=0;j<20;j++){
				long before	=	System.currentTimeMillis();
				long res	=	fibonacci(i);
				long after	=	System.currentTimeMillis();
				total	=	total+after-before;
			}
			System.out.println("Fibonacci "+i+" calculation average "+(total/20));
		}
	}

}
