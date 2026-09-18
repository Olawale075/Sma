const API_URL = "https://crop-disease-detector-8nqt.onrender.com";

// LOGIN
export async function login(phoneNumber, password) {
  // STEP 1: LOGIN
  const response = await fetch(`${API_URL}/user/auth/login`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      phoneNumber,
      password,
    }),
  });

  const contentType = response.headers.get("content-type");

  const data = contentType?.includes("application/json")
    ? await response.json()
    : await response.text();

  console.log(" LOGIN RESPONSE ");
  console.log(data);

  if (!response.ok) {
    throw new Error(
      typeof data === "object"
        ? data.message || data.error || "Login failed"
        : data || "Login failed"
    );
  }

  // STEP 2: SAVE TOKEN
  if (!data?.token) {
    throw new Error("Login succeeded but no token was returned.");
  }

  localStorage.setItem("token", data.token);

  // STEP 3: GET USER PHONE NUMBER
  // The login response only contains the JWT.
  // The JWT contains the user's phone number in "sub".
  let loggedInPhoneNumber = phoneNumber;

  try {
    const tokenParts = data.token.split(".");

    if (tokenParts.length === 3) {
      const payload = JSON.parse(
        atob(tokenParts[1].replace(/-/g, "+").replace(/_/g, "/"))
      );

      // console.log("JWT payload:", payload);

      if (payload.sub) {
        loggedInPhoneNumber = payload.sub;
      }
    }
  } catch (error) {
    console.warn(
      "Could not read phone number from JWT. Using login phone number.",
      error
    );
  }

  // console.log(
  //   "Phone number used to get user:",
  //   loggedInPhoneNumber
  // );

  // STEP 4: GET USER DETAILS
  const userResponse = await fetch(
    `${API_URL}/user/${encodeURIComponent(loggedInPhoneNumber)}`,
    {
      method: "GET",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${data.token}`,
      },
    }
  );

  const userContentType =
    userResponse.headers.get("content-type");

  const userData = userContentType?.includes("application/json")
    ? await userResponse.json()
    : await userResponse.text();

  // console.log("========== USER DETAILS ==========");
  // console.log(userData);
  // console.log("==================================");

  if (!userResponse.ok) {
    console.error(
      "Could not retrieve user details:",
      userData
    );

    // The login itself succeeded, so keep the token.
    // We just don't have the profile information yet.
    return data;
  }

  // STEP 5: SAVE USER DETAILS
  const user = {
    ...userData,

    // Make sure the Sidebar can use "name"
    name: userData.name || "User",

    // Keep the phone number consistent
    phoneNumbers:
      userData.phoneNumbers || loggedInPhoneNumber,
  };

  localStorage.setItem(
    "user",
    JSON.stringify(user)
  );

  // STEP 6: NOTIFY THE SIDEBAR
  window.dispatchEvent(
    new Event("userUpdated")
  );

  // console.log("========== USER SAVED ==========");
  // console.log(
  //   "Saved user:",
  //   JSON.parse(localStorage.getItem("user"))
  // );
  // console.log("Name:", user.name);
  // console.log("Phone:", user.phoneNumbers);
  // console.log("Farm:", user.farmName);
  // console.log("================================");

  return {
    ...data,
    user,
  };
}

// GET CURRENT USER
export async function getCurrentUser() {
  const token = localStorage.getItem("token");

  if (!token) {
    return null;
  }

  // Get phone number from JWT
  let phoneNumber = null;

  try {
    const tokenParts = token.split(".");

    if (tokenParts.length === 3) {
      const payload = JSON.parse(
        atob(tokenParts[1].replace(/-/g, "+").replace(/_/g, "/"))
      );

      phoneNumber = payload.sub;
    }
  } catch (error) {
    console.error(
      "Could not decode authentication token:",
      error
    );

    return null;
  }

  if (!phoneNumber) {
    return null;
  }

  // Get user details from backend
  const response = await fetch(
    `${API_URL}/user/${encodeURIComponent(phoneNumber)}`,
    {
      method: "GET",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
    }
  );

  const contentType =
    response.headers.get("content-type");

  const data = contentType?.includes("application/json")
    ? await response.json()
    : await response.text();

  if (!response.ok) {
    console.error(
      "Get current user failed:",
      data
    );

    return null;
  }

  // Save latest user information
  localStorage.setItem(
    "user",
    JSON.stringify(data)
  );

  window.dispatchEvent(
    new Event("userUpdated")
  );

  return data;
}

// SIGNUP
export async function signup(userData) {
  console.log("========== SIGNUP API REQUEST ==========");
  console.log("URL:", `${API_URL}/user/CreateUser`);
  console.log("Body:", userData);
  console.log("========================================");

  const response = await fetch(
    `${API_URL}/user/CreateUser`,
    {
      method: "POST",

      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
      },

      body: JSON.stringify(userData),
    }
  );

  const responseBody = await response.text();

  let data = responseBody;

  try {
    data = JSON.parse(responseBody);
  } catch {
    // Backend returned plain text
  }

  if (!response.ok) {
    throw new Error(
      typeof data === "object"
        ? data.message ||
          data.error ||
          `Signup failed with status ${response.status}`
        : data ||
          `Signup failed with status ${response.status}`
    );
  }

  return data;
}

// LOGOUT
export async function logout() {
  const token = localStorage.getItem("token");

  console.log(
    "Logout token:",
    token ? "Token exists" : "No token found"
  );

  try {
    // If there is no token, just clear everything locally
    if (!token) {
      console.log("No token found. Logging out locally.");

      localStorage.removeItem("token");
      localStorage.removeItem("user");

      window.dispatchEvent(new Event("userUpdated"));

      return true;
    }

    const response = await fetch(
      `${API_URL}/user/auth/logout`,
      {
        method: "POST",

        headers: {
          Authorization: `Bearer ${token}`,
        },

        credentials: "include",
      }
    );

    const contentType = response.headers.get("content-type");

    const data = contentType?.includes("application/json")
      ? await response.json()
      : await response.text();

    console.log("Logout status:", response.status);
    console.log("Logout response:", data);

    // Clear authentication regardless of backend response
    localStorage.removeItem("token");
    localStorage.removeItem("user");

    window.dispatchEvent(new Event("userUpdated"));

    if (!response.ok) {
      console.warn(
        "Backend logout failed, but local logout was completed."
      );

      return true;
    }

    console.log("Logout successful.");

    return true;

  } catch (error) {
    console.error("Logout request failed:", error);

    // Even if the server cannot be reached,
    // log the user out locally.
    localStorage.removeItem("token");
    localStorage.removeItem("user");

    window.dispatchEvent(new Event("userUpdated"));

    return true;
  }
}
