package healthABM;

import repast.simphony.random.RandomHelper;

public class TesterFlorian {

	public static void main(String[] args) {
		
		
		for(int i=0;i<10000;i++) {
			double res = Math.exp(RandomHelper.createNormal(6.767066678,0.4275681939).nextDouble()) ;
			System.out.println(res);
		}
		
		
	}

}
