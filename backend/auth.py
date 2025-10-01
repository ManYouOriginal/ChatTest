from datetime import datetime, timedelta
from jose import JWTError, jwt

SECRET_KEY = ".*|y`-`RH^i;(HYD6?ZpQW)B]7_~?rh3~>F_|{|wGJRC@-#EQ#_{SCTj:sIkC(n"
ALGHORITM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = 60*24*7

def create_access_token(data: dict, expires_delta: timedelta = None):
    to_encode = data.copy()
    if expires_delta:
        expire = datetime.utcnow() + expires_delta
    else:
        expire = datetime.utcnow() + timedelta(minutes = ACCESS_TOKEN_EXPIRE_MINUTES)
    to_encode.update({"exp": expire})
    encoded_jwt = jwt.encode(to_encode, SECRET_KEY, algorithm=ALGHORITM)
    return encoded_jwt

def decode_token(token: str):
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGHORITM])
        return payload
    except JWTError:
        return None
