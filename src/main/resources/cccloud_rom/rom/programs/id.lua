-- Shows the computer's id and label.
print("This is computer #" .. os.getComputerID())
local label = os.getComputerLabel()
if label then print("Its label is \"" .. label .. "\"") end
