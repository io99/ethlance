pragma solidity ^0.4.4;

import "ethlanceDB.sol";
import "safeMath.sol";

library SharedLibrary {
    function getCount(address db, string countKey) internal returns(uint) {
        return EthlanceDB(db).getUIntValue(sha3(countKey));
    }

    function createNext(address db, string countKey) internal returns(uint index) {
        var count = getCount(db, countKey);
        EthlanceDB(db).addUIntValue(sha3(countKey), 1);
        return count + 1;
    }

    function containsValue(address db, uint id, string key, uint8[] array) internal returns(bool) {
        if (array.length == 0) {
            return true;
        }
        var val = EthlanceDB(db).getUInt8Value(sha3(key, id));
        for (uint i = 0; i < array.length ; i++) {
            if (array[i] == val) {
                return true;
            }
        }
        return false;
    }

    function getArrayItemsCount(address db, uint id, string countKey) internal returns(uint) {
        return EthlanceDB(db).getUIntValue(sha3(countKey, id));
    }

    function addArrayItem(address db, uint id, string key, string countKey, uint val) internal {
        var idx = getArrayItemsCount(db, id, countKey);
        EthlanceDB(db).setUIntValue(sha3(key, id, idx), val);
        EthlanceDB(db).setUIntValue(sha3(countKey, id), idx + 1);
    }

    function setUIntArray(address db, uint id, string key, string countKey, uint[] array) internal{
        for (uint i = 0; i < array.length; i++) {
            EthlanceDB(db).setUIntValue(sha3(key, id, i), array[i]);
        }
        EthlanceDB(db).setUIntValue(sha3(countKey, id), array.length);
    }
    
    function getUIntArray(address db, uint id, string key, string countKey) internal returns(uint[] result) {
        uint count = getArrayItemsCount(db, id, countKey);
        result = new uint[](count);
        for (uint i = 0; i < count; i++) {
            result[i] = EthlanceDB(db).getUIntValue(sha3(key, id, i));
        }
        return result;
    }

    function addRemovableArrayItem(address db, uint[] ids, string key, string countKey, string keysKey, uint val) internal {
        if (ids.length == 0) {
            return;
        }
        for (uint i = 0; i < ids.length; i++) {
            addArrayItem(db, ids[i], keysKey, countKey, val);
            EthlanceDB(db).setBooleanValue(sha3(key, ids[i], val), true);
        }
    }

    function getRemovableArrayItems(address db, uint id, string key, string countKey, string keysKey)
        internal returns (uint[] result)
    {
        var count = getArrayItemsCount(db, id, countKey);
        result = new uint[](count);
        uint j = 0;
        for (uint i = 0; i < count; i++) {
            var itemId = EthlanceDB(db).getUIntValue(sha3(keysKey, id, i));
            if (EthlanceDB(db).getBooleanValue(sha3(key, id, itemId))) {
                result[j] = itemId;
                j++;
            }
        }
        return take(j, result);
    }

    function removeArrayItem(address db, uint[] ids, string key, uint val) internal {
        if (ids.length == 0) {
            return;
        }
        for (uint i = 0; i < ids.length; i++) {
            EthlanceDB(db).deleteBooleanValue(sha3(key, ids[i], val));
        }
    }

    function getPage(uint[] array, uint offset, uint limit) internal returns (uint[] result) {
        uint j = 0;
        if (offset >= array.length) {
            return result;
        }

        result = new uint[](limit);
        for (uint i = offset; i < (offset + limit); i++) {
            result[j] = array[i];
            j++;
            if (array.length == j) {
                break;
            }
        }
        return take(j, result);
    }

    function intersect(uint[] a, uint[] b) internal returns(uint[] c) {
        mapping (uint => bool) _map;
        if (a.length == 0 || b.length == 0) {
            return c;
        }
        c = new uint[](a.length);
        for (uint i = 0; i < a.length; i++) {
            _map[a[i]] = true;
        }
        uint j = 0;
        for (i = 0; i < b.length; i++) {
            if (_map[b[i]]) {
                c[j] = b[i];
                j++;
            }
        }
        return take(j, c);
    }

    function diff(uint[] _old, uint[] _new) internal returns(uint[] added, uint[] removed) {
        mapping (uint => uint8) _map;
        var maxCount = _old.length + _new.length;
        added = new uint[](maxCount);
        removed = new uint[](maxCount);

        for (uint i = 0; i < _old.length; i++) {
            _map[_old[i]] = 1;
        }
        uint addedCount = 0;
        for (i = 0; i < _new.length; i++) {
            if (_map[_new[i]] == 0) {
                added[addedCount] = _new[i];
                addedCount++;
            } else {
                _map[_new[i]] = 2;
            }
        }
        uint removedCount = 0;
        for (i = 0; i < _old.length; i++) {
            if (_map[_old[i]] == 1) {
                removed[removedCount] = _old[i];
                removedCount++;
            }
        }
        return (take(addedCount, added), take(removedCount, removed));
    }

    function take(uint n, uint[] array) internal returns(uint[] result) {
        result = new uint[](n);
        for (uint i = 0; i < n ; i++) {
            result[i] = array[i];
        }
        return result;
    }
    
    function intersectCategoriesAndSkills
    (
        address db,
        uint categoryId,
        uint[] skills,
        function(address, uint) returns (uint[] memory) getFromSkills,
        function(address, uint) returns (uint[] memory) getFromCategories,
        function(address) returns (uint) getMaxCount
    )
        internal returns (uint[] result)
    {
        var maxCount = getMaxCount(db);
        if (maxCount == 0) {
            return result;
        }

        if (skills.length > 0) {
            result = getFromSkills(db, skills[0]);
            for (uint i = 1; i < skills.length ; i++) {
                result = intersect(result, getFromSkills(db, skills[i]));
            }
        }

        if (categoryId > 0) {
            var catResult = getFromCategories(db, categoryId);
            if (skills.length == 0) {
                result = catResult;
            } else {
                result = intersect(result, catResult);
            }
        }
        return result;
    }

    function filter(
        address db,
        function (address, uint[] memory, uint) returns (bool) f,
        uint[] memory args,
        uint[] memory items
    )
        internal returns (uint[] memory r)
    {
        uint j = 0;
        r = new uint[](items.length);
        for (uint i = 0; i < items.length; i++) {
            if (f(db, args, items[i])) {
                r[j] = items[i];
                j++;
            }
        }
        return take(j, r);
    }

    function bytes32ToString(bytes32 x) internal returns (string) {
        bytes memory bytesString = new bytes(32);
        uint charCount = 0;
        for (uint j = 0; j < 32; j++) {
            byte char = byte(bytes32(uint(x) * 2 ** (8 * j)));
            if (char != 0) {
                bytesString[charCount] = char;
                charCount++;
            }
        }
        bytes memory bytesStringTrimmed = new bytes(charCount);
        for (j = 0; j < charCount; j++) {
            bytesStringTrimmed[j] = bytesString[j];
        }
        return string(bytesStringTrimmed);
    }

    function booleanToUInt(bool x) internal returns (uint) {
        if (x) {
            return 0;
        } else {
            return 1;
        }
    }

    function getUIntValue(address db, bytes32 record, uint8 uintType) internal returns(uint) {
        if (uintType == 1) {
            return booleanToUInt(EthlanceDB(db).getBooleanValue(record));
        } else if (uintType == 2) {
            return uint(EthlanceDB(db).getBytes32Value(record));
        } else if (uintType == 3) {
            return uint(EthlanceDB(db).getUInt8Value(record));
        } else {
            return EthlanceDB(db).getUIntValue(record);
        }
    }

    function getEntityList(address db, uint[] ids, bytes32[] fieldNames, uint8[] uintTypes)
            internal returns
    (
        uint[] items1,
        uint[] items2,
        uint[] items3,
        uint[] items4,
        uint[] items5,
        uint[] items6,
        uint[] items7
    )
    {
        for (uint i = 0; i < ids.length; i++) {
            items1[i] = ids[i];
            items2[i] = getUIntValue(db, sha3(bytes32ToString(fieldNames[1]), ids[i]), uintTypes[1]);
            items3[i] = getUIntValue(db, sha3(bytes32ToString(fieldNames[2]), ids[i]), uintTypes[2]);
            items4[i] = getUIntValue(db, sha3(bytes32ToString(fieldNames[3]), ids[i]), uintTypes[3]);
            items5[i] = getUIntValue(db, sha3(bytes32ToString(fieldNames[4]), ids[i]), uintTypes[4]);
            items6[i] = getUIntValue(db, sha3(bytes32ToString(fieldNames[5]), ids[i]), uintTypes[5]);
            items7[i] = getUIntValue(db, sha3(bytes32ToString(fieldNames[6]), ids[i]), uintTypes[6]);
        }

        return (items1, items2, items3, items4, items5, items6, items7);
    }
}