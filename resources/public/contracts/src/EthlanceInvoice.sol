pragma solidity ^0.4.24;

import "./EthlanceRegistry.sol";
import "./EthlanceWorkContract.sol";
import "./collections/EthlanceMetahash.sol";


/// @title Represents a Candidate Invoice for work
contract EthlanceInvoice is MetahashStore {
    uint public constant version = 1;
    EthlanceRegistry public constant registry = EthlanceRegistry(0xdaBBdABbDABbDabbDaBbDabbDaBbdaBbdaBbDAbB);

    //
    // Members
    //
    
    uint public date_created;
    
    uint public date_updated;

    // When date_paid is set >0, this reflects completion
    uint public date_paid;
    
    //FIXME: needs to be more flexible for other currency types
    //defined in the contract.

    // In Wei, the amount that needs to be paid to the candidate(employee)
    uint public amount_requested;
    
    // In Wei, the amount actually paid by the employer to the candidate(employee)
    uint public amount_paid;

    // The EthlanceWorkContract reference.
    EthlanceWorkContract public work_instance;  
    
    /// @dev Forwarder Constructor
    function construct(EthlanceWorkContract _work_instance, uint _amount_requested, string metahash) {
	// TODO: authenticate
	work_instance = _work_instance;
	appendCandidate(metahash);
	amount_requested = _amount_requested;
	date_created = now;
	date_updated = now;
    }

    
    function updateDateUpdated() private {
	date_updated = now;
    }


    /// @dev Append a metahash, which will identify the type of user
    /// and append to a MetahashStore
    /// @param metahash The metahash string you wish to append to hash listing.
    /*
      Notes:

      - Only the Candidate and Employer can append a metahash
        string. The metahash structure is predefined.

      - Retrieving data from the metahash store (getHashByIndex)
        should contain a comparison between the user_type and the data
        present to guarantee valid data from each constituent within
        the listing.

     */
    function appendMetahash(string metahash) external {
	if (work_instance.store_instance().employer_address() == msg.sender) {
	    appendEmployer(metahash);
	    updateDateUpdated();
	}
	else if (work_instance.candidate_address() == msg.sender) {
	    appendCandidate(metahash);
	    updateDateUpdated();
	}
	else {
	    revert("You are not privileged to append a comment.");
	}
    }


    /// @dev Pays the given invoice the the amount of `_amount_paid`.
    /// @param _amount_paid The amount to pay the invoice in Wei.
    function pay(uint _amount_paid) external {
	require(work_instance.store_instance().employer_address() == msg.sender,
		"Only the employer can pay an invoice.");
	require(date_paid == 0, "Given invoice has already been paid.");

	work_instance.payInvoice(_amount_paid);
	amount_paid = _amount_paid;
	date_paid = now;
	updateDateUpdated();
    }

    
    /// @dev Returns true if the invoice has been paid.
    function isInvoicePaid() external returns(bool) {
	return date_paid > 0;
    }
}
