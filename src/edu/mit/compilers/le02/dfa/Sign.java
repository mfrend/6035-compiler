package edu.mit.compilers.le02.dfa;

/**
 * Sign Abstraction for Multiplication for Data-Flow Analysis
 * @author Maria Frendberg(mfrend@mit.edu)
 *
 */
public enum Sign implements Type<Sign>{
	BOT,
	NEG,
	ZERO,
	POS,
	TOP;
	
	
	public Sign transferFunction(Sign sign){
		switch(this){
		case BOT:
			return sign;
		case NEG:
			switch(sign){
			case BOT:
			case POS:
				return NEG;
			case NEG:
				return POS;
			case ZERO:
				return ZERO;
			case TOP:
				return TOP;
			}
			break;
		case ZERO:
			return ZERO;
		case POS:
			switch(sign){
			case BOT:
				return POS;
			default:
				return sign;
			}
		case TOP:
			switch(sign){
			case ZERO:
				return ZERO;
			default:
				return TOP;
			}
		}
		return BOT;
		
	}
	
}
